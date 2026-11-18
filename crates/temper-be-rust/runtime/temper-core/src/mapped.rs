use super::{i32_to_usize, Error, List, Listed, Result, ToList};
use indexmap::IndexMap;
use std::{
    cell::RefCell,
    hash::Hash,
    sync::{Arc, RwLock},
};

pub trait MappedTrait<K, V>: Sync + Send
where
    K: Clone + Sync + Send + Eq + Hash,
    V: Clone + Sync + Send,
{
    fn for_each(&self, action: &dyn Fn(K, V));

    // TODO Receive &K? And more references all throughout be-rust?
    // TODO &Q where K: Borrow<Q>? See also indexmap::Equivalent.
    fn get(&self, key: K) -> Result<V>;

    fn get_or(&self, key: K, fallback: V) -> V;

    fn has(&self, key: K) -> bool;

    fn keys(&self) -> Listed<K>;

    fn len(&self) -> i32;

    fn to_list(&self) -> List<(K, V)>;

    fn to_map(&self) -> Map<K, V>;

    fn to_map_builder(&self) -> MapBuilder<K, V>;

    fn values(&self) -> Listed<V>;
}

pub fn mapped_to_list_with<K, V, T>(
    mapped: &dyn MappedTrait<K, V>,
    action: &dyn Fn(K, V) -> T,
) -> List<T>
where
    K: Clone + Sync + Send + Eq + Hash,
    V: Clone + Sync + Send,
{
    let result: RefCell<Vec<T>> = RefCell::new(vec![]);
    mapped.for_each(&mut |key, value| {
        result.borrow_mut().push(action(key, value));
    });
    result.into_inner().to_list()
}

#[derive(Clone)]
pub struct Mapped<K, V>(Arc<dyn MappedTrait<K, V>>);

impl<K, V> Mapped<K, V>
where
    K: Clone + Sync + Send + Eq + Hash,
    V: Clone + Sync + Send,
{
    pub fn new(selfish: impl MappedTrait<K, V> + 'static) -> Mapped<K, V> {
        Mapped(Arc::new(selfish))
    }
}

impl<K, V> std::ops::Deref for Mapped<K, V> {
    type Target = dyn MappedTrait<K, V>;
    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

#[derive(Clone)]
pub struct Map<K, V>(Arc<IndexMap<K, V>>);

impl<K, V> Map<K, V>
where
    K: Clone + Eq + Hash,
    V: Clone,
{
    pub fn new(entries: impl ToList<(K, V)>) -> Map<K, V> {
        let entries = entries.to_list();
        let mut internal = IndexMap::with_capacity(entries.len());
        // TODO If we can implement IntoIterator for Listed, this might be nicer.
        for i in 0..entries.len() {
            let (key, value) = entries.get(i).unwrap();
            internal.insert(key.clone(), value.clone());
        }
        Map(Arc::new(internal))
    }
}

impl<K, V> MappedTrait<K, V> for Map<K, V>
where
    K: 'static + Clone + Sync + Send + Eq + Hash,
    V: 'static + Clone + Sync + Send,
{
    fn for_each(&self, action: &dyn Fn(K, V)) {
        for (key, value) in self.0.iter() {
            action(key.clone(), value.clone());
        }
    }

    fn get(&self, key: K) -> Result<V> {
        Ok(self.0.get(&key).ok_or_else(Error::new)?.clone())
    }

    fn get_or(&self, key: K, fallback: V) -> V {
        self.0.get(&key).cloned().unwrap_or(fallback)
    }

    fn has(&self, key: K) -> bool {
        self.0.contains_key(&key)
    }

    fn keys(&self) -> Listed<K> {
        Listed::new(self.0.keys().cloned().collect::<Vec<_>>().to_list())
    }

    fn len(&self) -> i32 {
        self.0.len().try_into().unwrap()
    }

    fn to_list(&self) -> List<(K, V)> {
        self.0
            .iter()
            .map(|(key, value)| (key.clone(), value.clone()))
            .collect::<Vec<_>>()
            .to_list()
    }

    fn to_map(&self) -> Map<K, V> {
        self.clone()
    }

    fn to_map_builder(&self) -> MapBuilder<K, V> {
        MapBuilder(RwLock::new((*self.0).clone()).into())
    }

    fn values(&self) -> Listed<V> {
        Listed::new(self.0.values().cloned().collect::<Vec<_>>().to_list())
    }
}

#[derive(Clone)]
pub struct MapBuilder<K, V>(Arc<RwLock<IndexMap<K, V>>>);

impl<K, V> MapBuilder<K, V>
where
    K: Clone + Eq + Hash,
    V: Clone,
{
    pub fn new() -> MapBuilder<K, V> {
        MapBuilder(RwLock::new(IndexMap::new()).into())
    }

    pub fn clear(&self) {
        self.0.write().unwrap().clear();
    }

    // Not public!
    fn get_index(&self, index: i32) -> Option<(K, V)> {
        let index = i32_to_usize(index).unwrap();
        let internal = self.0.read().unwrap();
        let (key, value) = internal.get_index(index)?;
        Some(((*key).clone(), (*value).clone()))
    }

    pub fn remove(&self, key: K) -> Result<V> {
        self.0
            .write()
            .unwrap()
            .shift_remove(&key)
            .ok_or_else(Error::new)
    }

    pub fn set(&self, key: K, value: V) {
        self.0.write().unwrap().insert(key, value);
    }
}

impl<K, V> MappedTrait<K, V> for MapBuilder<K, V>
where
    K: 'static + Clone + Sync + Send + Eq + Hash,
    V: 'static + Clone + Sync + Send,
{
    fn for_each(&self, action: &dyn Fn(K, V)) {
        for index in 0..self.len() {
            let Some((key, value)) = self.get_index(index) else {
                // This should only happen if the map builder changes during
                // iteration. For now, just exit early, but we need to decide on
                // consistent semantics for mutation while looping.
                return;
            };
            action(key, value);
        }
    }

    fn get(&self, key: K) -> Result<V> {
        Ok(self
            .0
            .read()
            .unwrap()
            .get(&key)
            .ok_or_else(Error::new)?
            .clone())
    }

    fn get_or(&self, key: K, fallback: V) -> V {
        self.0
            .read()
            .unwrap()
            .get(&key)
            .cloned()
            .unwrap_or(fallback)
    }

    fn has(&self, key: K) -> bool {
        self.0.read().unwrap().contains_key(&key)
    }

    fn keys(&self) -> Listed<K> {
        Listed::new(
            self.0
                .read()
                .unwrap()
                .keys()
                .cloned()
                .collect::<Vec<_>>()
                .to_list(),
        )
    }

    fn len(&self) -> i32 {
        self.0.read().unwrap().len().try_into().unwrap()
    }

    fn to_list(&self) -> List<(K, V)> {
        self.0
            .read()
            .unwrap()
            .iter()
            .map(|(key, value)| (key.clone(), value.clone()))
            .collect::<Vec<_>>()
            .to_list()
    }

    fn to_map(&self) -> Map<K, V> {
        Map(self.0.read().unwrap().clone().into())
    }

    fn to_map_builder(&self) -> MapBuilder<K, V> {
        MapBuilder(RwLock::new(self.0.read().unwrap().clone()).into())
    }

    fn values(&self) -> Listed<V> {
        Listed::new(
            self.0
                .read()
                .unwrap()
                .values()
                .cloned()
                .collect::<Vec<_>>()
                .to_list(),
        )
    }
}

pub trait Pair<K, V>
where
    K: Clone,
    V: Clone,
{
    fn key(&self) -> K;
    fn value(&self) -> V;
}

impl<K, V> Pair<K, V> for (K, V)
where
    K: Clone,
    V: Clone,
{
    fn key(&self) -> K {
        self.0.clone()
    }

    fn value(&self) -> V {
        self.1.clone()
    }
}
