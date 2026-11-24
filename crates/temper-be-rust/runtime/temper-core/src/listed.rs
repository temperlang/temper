use super::index_out_of_bounds;
use std::{
    cell::RefCell,
    ops::Deref,
    sync::{Arc, RwLock},
};

/// The value at `index` of `items`, or a panic out of bounds.
fn item<T: Clone>(items: &[T], index: i32) -> T {
    match usize::try_from(index).ok().and_then(|i| items.get(i)) {
        Some(value) => value.clone(),
        None => index_out_of_bounds(index, items.len()),
    }
}

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
//
// Those that call back walk a snapshot of the items (`to_list`, which
// copies only a builder's), as the interpreter does, holding no lock
// while the callback runs, so a callback may read or change a builder it
// walks without deadlocking or reading past its end.
mod listed {
    use super::{List, ListedTrait};
    use crate::ToArcString;
    use std::{cell::RefCell, sync::Arc};

    pub fn filter<T>(listed: &dyn ListedTrait<T>, predicate: &dyn Fn(T) -> bool) -> List<T>
    where
        T: Clone + Sync + Send,
    {
        let items = listed.to_list();
        let mut result = vec![];
        for value in items.iter() {
            if predicate(value.clone()) {
                result.push(value.clone());
            }
        }
        Arc::new(result)
    }

    pub fn for_each<T>(listed: &dyn ListedTrait<T>, action: &dyn Fn(T))
    where
        T: Clone + Sync + Send,
    {
        for value in listed.to_list().iter() {
            action(value.clone());
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
        for (i, value) in listed.to_list().iter().enumerate() {
            if i > 0 {
                result.push_str(separator.as_str());
            }
            result.push_str(&stringify(value.clone()));
        }
        result.to_arc_string()
    }

    pub fn map<T, O>(listed: &dyn ListedTrait<T>, transform: &dyn Fn(T) -> O) -> List<O>
    where
        T: Clone + Sync + Send,
    {
        let items = listed.to_list();
        let mut result = Vec::with_capacity(items.as_slice().len());
        for value in items.iter() {
            result.push(transform(value.clone()));
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

    /// `reduceFromIndex`, from `index`, none past the end; a negative
    /// index fails as `get` does.
    pub fn reduce_from_index<T, O>(
        listed: &dyn ListedTrait<T>,
        initial: O,
        index: i32,
        accumulate: &dyn Fn(O, T) -> O,
    ) -> O
    where
        T: Clone + Sync + Send,
        O: Clone + Sync + Send,
    {
        if index < 0 {
            listed.get(index);
        }
        let items = listed.to_list();
        let mut result = initial;
        for value in items.iter().skip(index as usize) {
            result = accumulate(result, value.clone());
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
        item(self, index)
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
    use super::{List, ListBuilder, ToListed, index_out_of_bounds};
    use std::sync::{Arc, RwLock};

    /// `index` as a place to insert into `items` of `length`, from 0 to
    /// `length`, or a panic.
    fn insertion_index(index: i32, length: usize) -> usize {
        match usize::try_from(index) {
            Ok(at) if at <= length => at,
            _ => index_out_of_bounds(index, length),
        }
    }

    pub fn add<T>(builder: &ListBuilder<T>, value: T, index: Option<i32>) -> () {
        let mut builder = builder.write().unwrap();
        match index {
            Some(index) => {
                let index = insertion_index(index, builder.len());
                builder.insert(index, value);
            }
            None => builder.push(value),
        }
    }

    pub fn add_all<T>(builder: &ListBuilder<T>, values: impl ToListed<T>, index: Option<i32>) -> ()
    where
        T: Clone + Sync + Send,
    {
        // Copy the values before locking: they may be this builder, whose
        // values as they were before the call are added, as Python's do.
        let values = values.to_listed().to_vec();
        let mut builder = builder.write().unwrap();
        let index = match index {
            Some(index) => insertion_index(index, builder.len()),
            None => builder.len(),
        };
        builder.splice(index..index, values);
    }

    pub fn clear<T>(builder: &ListBuilder<T>) {
        builder.write().unwrap().clear();
    }

    pub fn new_builder<T>() -> ListBuilder<T> {
        Arc::new(RwLock::new(vec![]))
    }

    pub fn remove_last<T>(builder: &ListBuilder<T>) -> T {
        let last = builder.write().unwrap().pop();
        last.unwrap_or_else(|| index_out_of_bounds(-1, 0))
    }

    pub fn reverse<T>(builder: &ListBuilder<T>) {
        builder.write().unwrap().reverse();
    }

    pub fn set<T>(builder: &ListBuilder<T>, index: i32, value: T) {
        let mut items = builder.write().unwrap();
        let length = items.len();
        match usize::try_from(index).ok().and_then(|i| items.get_mut(i)) {
            Some(slot) => *slot = value,
            None => index_out_of_bounds(index, length),
        }
    }

    /// Sorts a copy, holding no lock while comparing, so the comparison
    /// may read the builder, and a panic in it leaves the builder as it
    /// was rather than poisoned. A comparison that changes the builder's
    /// length panics, as Java's and Python's sorts fail.
    pub fn sort<T>(builder: &ListBuilder<T>, compare: &dyn Fn(T, T) -> i32)
    where
        T: Clone + Sync + Send,
    {
        let mut items = builder.read().unwrap().clone();
        let len = items.len();
        items.sort_by(|a, b| compare(a.clone(), b.clone()).cmp(&0));
        let mut stored = builder.write().unwrap();
        if stored.len() != len {
            panic!("ListBuilder changed length during sort");
        }
        *stored = items;
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
        // Copy the values before locking, as `add_all` does.
        let values = match values {
            Some(values) => values.to_listed().to_vec(),
            None => vec![],
        };
        let mut builder = builder.write().unwrap();
        let begin = index.unwrap_or(0).max(0) as usize;
        let remove_count: usize = match remove_count {
            Some(remove_count) => remove_count.max(0).try_into().unwrap(),
            None => builder.len(),
        };
        let end = begin + remove_count.min(builder.len() - begin);
        Arc::new(builder.splice(begin..end, values).collect())
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
        item(&self.read().unwrap(), index)
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
