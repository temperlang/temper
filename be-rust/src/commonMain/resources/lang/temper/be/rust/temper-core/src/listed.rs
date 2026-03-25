use super::i32_to_usize;
use std::{
    cell::RefCell,
    ops::Deref,
    sync::{Arc, RwLock},
};

pub trait ListedTrait<T>: Sync + Send
where
    T: Clone + Sync + Send,
{
    fn clone_box(&self) -> Listed<T>;

    fn get(&self, index: i32) -> T;

    fn get_or(&self, index: i32, fallback: T) -> T {
        match () {
            _ if index >= 0 && index < self.len() => self.get(index),
            _ => fallback,
        }
    }

    fn is_empty(&self) -> bool;

    fn len(&self) -> i32;

    fn to_list(&self) -> List<T>;

    fn to_list_builder(&self) -> ListBuilder<T> {
        Arc::new(RwLock::new(self.to_vec()))
    }

    fn to_vec(&self) -> Vec<T> {
        let result: RefCell<Vec<T>> = RefCell::new(vec![]);
        self.with_vec(&|values: &Vec<T>| {
            result.replace(values.clone());
        });
        result.into_inner()
    }

    // TODO This is exposed. Good idea to keep it or not?
    fn with_vec(&self, action: &dyn Fn(&Vec<T>));
}

pub struct Listed<T>(Box<dyn ListedTrait<T>>)
where
    T: Clone;

impl<T> Deref for Listed<T>
where
    T: Clone,
{
    type Target = dyn ListedTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

impl<T> Listed<T>
where
    T: Clone + Sync + Send,
{
    pub fn new(x: impl ListedTrait<T> + 'static) -> Self {
        Self(Box::new(x))
    }

    pub fn join(
        &self,
        separator: impl crate::ToArcString,
        stringify: &dyn Fn(T) -> Arc<String>,
    ) -> Arc<String> {
        join(&**self, separator, stringify)
    }

    pub fn sorted(&self, compare: &dyn Fn(T, T) -> i32) -> List<T> {
        sorted(&**self, compare)
    }
}

// Use a submodule for source grouping, but still just expose the content.
pub use listed::*;

// TODO Just switch to using <ListedTrait<_>>::whatever calls where needed?
mod listed {
    use super::{List, ListedTrait};
    use crate::ToArcString;
    use std::{cell::RefCell, sync::Arc};

    pub fn filter<T>(listed: &dyn ListedTrait<T>, predicate: &dyn Fn(T) -> bool) -> List<T>
    where
        T: Clone + Sync + Send,
    {
        // We could make a callback option for holding any lock on a vec,
        // but that risks deadlock.
        // So just loop externally, potentially paying more cost.
        let mut result = vec![];
        for i in 0..listed.len() {
            let value = listed.get(i);
            if predicate(value.clone()) {
                result.push(value);
            }
        }
        Arc::new(result)
    }

    pub fn for_each<T>(listed: &dyn ListedTrait<T>, action: &dyn Fn(T))
    where
        T: Clone + Sync + Send,
    {
        for i in 0..listed.len() {
            action(listed.get(i));
        }
    }

    pub fn join<T>(
        listed: &dyn ListedTrait<T>,
        separator: impl ToArcString,
        stringify: &dyn Fn(T) -> Arc<String>,
    ) -> Arc<String>
    where
        T: Clone + Sync + Send,
    {
        let separator = separator.to_arc_string();
        let mut result = String::new();
        for i in 0..listed.len() {
            if i > 0 {
                result.push_str(separator.as_str());
            }
            result.push_str(&stringify(listed.get(i)));
        }
        result.to_arc_string()
    }

    pub fn map<T, O>(listed: &dyn ListedTrait<T>, transform: &dyn Fn(T) -> O) -> List<O>
    where
        T: Clone + Sync + Send,
    {
        let mut result = vec![];
        for i in 0..listed.len() {
            result.push(transform(listed.get(i)));
        }
        Arc::new(result)
    }

    pub fn reduce<T>(listed: &dyn ListedTrait<T>, accumulate: &dyn Fn(T, T) -> T) -> T
    where
        T: Clone + Sync + Send,
    {
        reduce_from_index(listed, listed.get(0), 1, accumulate)
    }

    pub fn reduce_from<T, O>(
        listed: &dyn ListedTrait<T>,
        initial: O,
        accumulate: &dyn Fn(O, T) -> O,
    ) -> O
    where
        T: Clone + Sync + Send,
        O: Clone + Sync + Send,
    {
        reduce_from_index(listed, initial, 0, accumulate)
    }

    fn reduce_from_index<T, O>(
        listed: &dyn ListedTrait<T>,
        initial: O,
        index: i32,
        accumulate: &dyn Fn(O, T) -> O,
    ) -> O
    where
        T: Clone + Sync + Send,
        O: Clone + Sync + Send,
    {
        let mut result = initial;
        for i in index..listed.len() {
            result = accumulate(result, listed.get(i));
        }
        result
    }

    pub fn slice<T>(listed: &dyn ListedTrait<T>, begin: i32, end: i32) -> List<T>
    where
        T: Clone + Sync + Send,
    {
        let begin = begin.max(0) as usize;
        let end = end.max(0).min(listed.len()) as usize;
        let result: RefCell<Vec<T>> = RefCell::new(vec![]);
        listed.with_vec(&|values: &Vec<T>| {
            result.replace(values[begin..end].to_vec());
        });
        Arc::new(result.into_inner())
    }

    pub fn sorted<T>(listed: &dyn ListedTrait<T>, compare: &dyn Fn(T, T) -> i32) -> List<T>
    where
        T: Clone + Sync + Send,
    {
        let mut result = listed.to_vec();
        result.sort_by(|a, b| compare(a.clone(), b.clone()).cmp(&0));
        Arc::new(result)
    }
}

impl<T> Clone for Listed<T>
where
    T: Clone + Sync + Send,
{
    fn clone(&self) -> Self {
        self.clone_box()
    }
}

impl<T> From<Vec<T>> for Listed<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn from(value: Vec<T>) -> Self {
        value.to_listed()
    }
}

impl<T, const N: usize> From<[T; N]> for Listed<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn from(value: [T; N]) -> Self {
        value.to_listed()
    }
}

pub trait ToListed<T>
where
    T: Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T>;
}

impl<T> ToListed<T> for Listed<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T> {
        self
    }
}

impl<T> ToListed<T> for Vec<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T> {
        Listed::new(Arc::new(self))
    }
}

impl<T, const N: usize> ToListed<T> for [T; N]
where
    T: 'static + Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T> {
        self.to_vec().to_listed()
    }
}

pub type List<T> = Arc<Vec<T>>;

pub fn list_for_each<T>(list: &[T], action: &dyn Fn(T))
where
    T: Clone,
{
    for item in list {
        action(item.clone())
    }
}

impl<T> ToListed<T> for List<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T> {
        Listed::new(self)
    }
}

pub trait ToList<T> {
    fn to_list(self) -> List<T>;
}

impl<T> ToList<T> for List<T> {
    fn to_list(self) -> List<T> {
        self
    }
}

impl<T> ToList<T> for Vec<T> {
    fn to_list(self) -> List<T> {
        Arc::new(self)
    }
}

impl<T, const N: usize> ToList<T> for [T; N]
where
    T: Clone + Sync + Send,
{
    fn to_list(self) -> List<T> {
        Arc::new(self.to_vec())
    }
}

impl<T, const N: usize> ToList<T> for &[T; N]
where
    T: Clone + Sync + Send,
{
    fn to_list(self) -> List<T> {
        Arc::new(self.to_vec())
    }
}

impl<T> From<List<T>> for Listed<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn from(value: List<T>) -> Self {
        Listed::new(value)
    }
}

impl<T> ListedTrait<T> for List<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn clone_box(&self) -> Listed<T> {
        Listed::new(self.clone())
    }

    fn get(&self, index: i32) -> T {
        <[T]>::get(self, i32_to_usize(index).unwrap())
            .unwrap()
            .clone()
    }

    fn is_empty(&self) -> bool {
        <Vec<T>>::is_empty(self)
    }

    fn len(&self) -> i32 {
        <Vec<T>>::len(self).try_into().unwrap()
    }

    fn to_list(&self) -> List<T> {
        self.clone()
    }

    fn with_vec(&self, action: &dyn Fn(&Vec<T>)) {
        action(&**self);
    }
}

pub type ListBuilder<T> = Arc<RwLock<Vec<T>>>;

impl<T> ToListed<T> for ListBuilder<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn to_listed(self) -> Listed<T> {
        Listed::new(self)
    }
}

// Use a submodule for source grouping, but still just expose the content.
pub use list_builder::*;

pub mod list_builder {
    use super::{List, ListBuilder, Listed, ToListed};
    use std::{
        cell::RefCell,
        sync::{Arc, RwLock},
    };

    pub fn add<T>(builder: &ListBuilder<T>, value: T, index: Option<i32>) -> () {
        match index {
            Some(index) => {
                let mut builder = builder.write().unwrap();
                let index = index.try_into().unwrap();
                builder.insert(index, value);
            }
            None => {
                builder.write().unwrap().push(value);
            }
        }
    }

    pub fn add_all<T>(builder: &ListBuilder<T>, values: impl ToListed<T>, index: Option<i32>) -> ()
    where
        T: Clone + Sync + Send,
    {
        let values = values.to_listed();
        let builder = RefCell::new(builder.write().unwrap());
        let index = match index {
            Some(index) => index.try_into().unwrap(),
            None => builder.borrow().len(),
        };
        values.with_vec(&|values: &Vec<T>| {
            builder
                .borrow_mut()
                .splice(index..index, values.iter().cloned());
        });
    }

    pub fn clear<T>(builder: &ListBuilder<T>) {
        builder.write().unwrap().clear();
    }

    pub fn new_builder<T>() -> ListBuilder<T> {
        Arc::new(RwLock::new(vec![]))
    }

    pub fn remove_last<T>(builder: &ListBuilder<T>) -> T {
        builder.write().unwrap().pop().unwrap()
    }

    pub fn reverse<T>(builder: &ListBuilder<T>) {
        builder.write().unwrap().reverse();
    }

    pub fn set<T>(builder: &ListBuilder<T>, index: i32, value: T) {
        let index: usize = index.try_into().unwrap();
        builder.write().unwrap()[index] = value;
    }

    pub fn sort<T>(builder: &ListBuilder<T>, compare: &dyn Fn(T, T) -> i32)
    where
        T: Clone + Sync + Send,
    {
        builder
            .write()
            .unwrap()
            .sort_by(|a, b| compare(a.clone(), b.clone()).cmp(&0));
    }

    pub fn splice<T>(
        builder: &ListBuilder<T>,
        index: Option<i32>,
        remove_count: Option<i32>,
        values: Option<impl ToListed<T>>,
    ) -> List<T>
    where
        T: 'static + Clone + Sync + Send,
    {
        let values = match values {
            Some(values) => values.to_listed(),
            None => Listed::new(Arc::new(vec![])),
        };
        let builder = RefCell::new(builder.write().unwrap());
        let begin = index.unwrap_or(0).max(0) as usize;
        let remove_count: usize = match remove_count {
            Some(remove_count) => remove_count.max(0).try_into().unwrap(),
            None => builder.borrow().len(),
        };
        let end = begin + remove_count.min(builder.borrow().len() - begin);
        let result: RefCell<Vec<T>> = RefCell::new(vec![]);
        values.with_vec(&|values: &Vec<T>| {
            result.replace(
                builder
                    .borrow_mut()
                    .splice(begin..end, values.iter().cloned())
                    .collect(),
            );
        });
        Arc::new(result.into_inner())
    }
}

pub trait ToListBuilder<T> {
    fn to_list_builder(self) -> ListBuilder<T>;
}

impl<T> ToListBuilder<T> for ListBuilder<T> {
    fn to_list_builder(self) -> ListBuilder<T> {
        self
    }
}

impl<T> ToListBuilder<T> for Vec<T> {
    fn to_list_builder(self) -> ListBuilder<T> {
        Arc::new(RwLock::new(self))
    }
}

impl<T> From<ListBuilder<T>> for Listed<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn from(value: ListBuilder<T>) -> Self {
        Listed::new(value)
    }
}

impl<T> ListedTrait<T> for ListBuilder<T>
where
    T: 'static + Clone + Sync + Send,
{
    fn clone_box(&self) -> Listed<T> {
        Listed::new(self.clone())
    }

    fn get(&self, index: i32) -> T {
        <[T]>::get(&self.read().unwrap(), i32_to_usize(index).unwrap())
            .unwrap()
            .clone()
    }

    fn is_empty(&self) -> bool {
        self.read().unwrap().is_empty()
    }

    fn len(&self) -> i32 {
        self.read().unwrap().len().try_into().unwrap()
    }

    fn to_list(&self) -> List<T> {
        Arc::new(self.to_vec())
    }

    fn with_vec(&self, action: &dyn Fn(&Vec<T>)) {
        let values = self.read().unwrap();
        action(&*values);
    }
}
