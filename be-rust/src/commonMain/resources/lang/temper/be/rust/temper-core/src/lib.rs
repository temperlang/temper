use std::any::{Any, TypeId};
use std::collections::VecDeque;
use std::fmt;
use std::mem::forget;
use std::sync::{Arc, RwLock};

pub mod float64;
pub mod generator;
pub use generator::{SafeGenerator, SafeGeneratorTrait};
pub mod listed;
pub use listed::{List, ListBuilder, Listed, ListedTrait, ToList, ToListBuilder, ToListed};
pub mod mapped;
pub use mapped::{mapped_to_list_with, Map, MapBuilder, Mapped, MappedTrait, Pair};
pub mod promise;
pub use promise::{
    AsyncRunner, AsyncRunnerTrait, Promise, PromiseBuilder, SingleThreadAsyncRunner, Task,
};
pub mod string;
pub use string::AsStr;

// Accessors

// Putting these in a separate scope avoids deadlock when in a context where
// there's also a write to the same lock.

pub fn read_locked<T: Clone>(x: &Arc<RwLock<T>>) -> T {
    x.read().unwrap().clone()
}

// AnyValue

#[macro_export]
macro_rules! impl_any_value_trait { // for concrete types
    // Two versions here. One for type args and one without.
    ($type:ident<$($param:tt),*>, [$($target:ty),*]) => {
        impl<$($param: Clone + Send + Sync + 'static),*> temper_core::AnyValueTrait for $type<$($param),*> {
            fn cast(&self, type_id: std::any::TypeId) -> Option<Box<dyn std::any::Any>> {
                match () {
                    // Check the concrete type first, expecting it to be most common.
                    _ if type_id == std::any::TypeId::of::<$type<$($param),*>>() => Some(Box::new(self.clone())),
                    $(
                        _ if type_id == std::any::TypeId::of::<$target>() => {
                            Some(Box::new(<$target>::new(self.clone())))
                        }
                    )*
                    _ => None,
                }
            }
            fn is(&self, type_id: std::any::TypeId) -> bool {
                type_id == std::any::TypeId::of::<$type<$($param),*>>()
                $(|| type_id == std::any::TypeId::of::<$target>())*
            }
            fn ptr_id(&self) -> usize {
                // And note that we implement ptr id only for concrete types. Abstracts delegate.
                std::sync::Arc::as_ptr(&self.0) as usize
            }
        }
        impl<$($param: Clone + Send + Sync + 'static),*> temper_core::AsAnyValue for $type<$($param),*> {
            fn as_any_value(&self) -> temper_core::AnyValue {
                temper_core::AnyValue::new(self.clone())
            }
        }
    };
    ($type:ident, [$($target:ty),*]) => {
        impl temper_core::AnyValueTrait for $type {
            fn cast(&self, type_id: std::any::TypeId) -> Option<Box<dyn std::any::Any>> {
                match () {
                    // Check the concrete type first, expecting it to be most common.
                    _ if type_id == std::any::TypeId::of::<$type>() => Some(Box::new(self.clone())),
                    $(
                        _ if type_id == std::any::TypeId::of::<$target>() => {
                            Some(Box::new(<$target>::new(self.clone())))
                        }
                    )*
                    _ => None,
                }
            }
            fn is(&self, type_id: std::any::TypeId) -> bool {
                type_id == std::any::TypeId::of::<$type>()
                $(|| type_id == std::any::TypeId::of::<$target>())*
            }
            fn ptr_id(&self) -> usize {
                std::sync::Arc::as_ptr(&self.0) as usize
            }
        }
        impl temper_core::AsAnyValue for $type {
            fn as_any_value(&self) -> temper_core::AnyValue {
                temper_core::AnyValue::new(self.clone())
            }
        }
    };
}

#[macro_export]
macro_rules! impl_any_value_trait_for_interface { // for abstract types
    // Again, one for type args, and one without.
    ($type:ident<$($param:tt),*>) => {
        impl<$($param: Clone + Send + Sync + 'static),*> temper_core::AsAnyValue for $type<$($param),*> {
            fn as_any_value(&self) -> temper_core::AnyValue {
                self.0.as_any_value()
            }
        }
    };
    ($type:ty) => {
        impl temper_core::AsAnyValue for $type {
            fn as_any_value(&self) -> temper_core::AnyValue {
                self.0.as_any_value()
            }
        }
    };
}

pub trait AsAnyValue {
    fn as_any_value(&self) -> AnyValue;
}

pub fn cast<T: 'static>(any_value: impl AsAnyValue) -> Option<T> {
    let any_value = any_value.as_any_value();
    let type_id = TypeId::of::<T>();
    if type_id == TypeId::of::<AnyValue>() {
        // We have an AnyValue, but we need to convince Rust that it's a T.
        // This special case prevents needing to handle it in every impl.
        let ptr = &any_value as *const AnyValue as *const T;
        let result = Some(unsafe { ptr.read() });
        // But don't decrement the instance above, since ptr read doesn't move.
        forget(any_value);
        return result;
    }
    let down = any_value.cast(type_id)?.downcast::<T>();
    Some(*down.expect("valid cast result"))
}

pub fn is<T: 'static>(any_value: impl AsAnyValue) -> bool {
    let type_id = TypeId::of::<T>();
    if type_id == TypeId::of::<AnyValue>() {
        return true;
    }
    any_value.as_any_value().is(type_id)
}

pub trait AnyValueTrait: Send + Sync {
    // Supports also casting to supertraits of the base type.
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>>;
    fn is(&self, type_id: TypeId) -> bool;
    fn ptr_id(&self) -> usize;
}

#[derive(Clone)]
pub struct AnyValue(std::sync::Arc<dyn AnyValueTrait>);

impl AnyValue {
    pub fn new(selfish: impl AnyValueTrait + 'static) -> AnyValue {
        AnyValue(std::sync::Arc::new(selfish))
    }
}

impl AnyValueTrait for AnyValue {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        self.0.cast(type_id)
    }

    fn is(&self, type_id: TypeId) -> bool {
        self.0.is(type_id)
    }

    fn ptr_id(&self) -> usize {
        self.0.ptr_id()
    }
}

impl AsAnyValue for AnyValue {
    fn as_any_value(&self) -> AnyValue {
        self.clone()
    }
}

impl std::ops::Deref for AnyValue {
    type Target = dyn AnyValueTrait;
    fn deref(&self) -> &Self::Target {
        &(*self.0)
    }
}

// AnyValue for ()

impl AnyValueTrait for () {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<()>() => Some(Box::new(*self)),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<()>()
    }

    fn ptr_id(&self) -> usize {
        self as *const _ as usize
    }
}

impl AsAnyValue for () {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(*self)
    }
}

// AnyValue for bool

impl AnyValueTrait for bool {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<bool>() => Some(Box::new(*self)),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<bool>()
    }

    fn ptr_id(&self) -> usize {
        self as *const _ as usize
    }
}

impl AsAnyValue for bool {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(*self)
    }
}

// AnyValue for f64

impl AnyValueTrait for f64 {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<f64>() => Some(Box::new(*self)),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<f64>()
    }

    fn ptr_id(&self) -> usize {
        self as *const _ as usize
    }
}

impl AsAnyValue for f64 {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(*self)
    }
}

// AnyValue for i32

impl AnyValueTrait for i32 {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<i32>() => Some(Box::new(*self)),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<i32>()
    }

    fn ptr_id(&self) -> usize {
        // Shouldn't actually use this for ints. TODO Keep out of AnyValueTrait?
        self as *const _ as usize
    }
}

impl AsAnyValue for i32 {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(*self)
    }
}

// AnyValue for i64

impl AnyValueTrait for i64 {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<i64>() => Some(Box::new(*self)),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<i64>()
    }

    fn ptr_id(&self) -> usize {
        // Shouldn't actually use this for ints. TODO Keep out of AnyValueTrait?
        self as *const _ as usize
    }
}

impl AsAnyValue for i64 {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(*self)
    }
}

// AnyValue for Arc<String>

impl AnyValueTrait for Arc<String> {
    fn cast(&self, type_id: TypeId) -> Option<Box<dyn Any>> {
        match () {
            _ if type_id == TypeId::of::<Arc<String>>() => Some(Box::new(self.clone())),
            _ => None,
        }
    }

    fn is(&self, type_id: TypeId) -> bool {
        type_id == TypeId::of::<Arc<String>>()
    }

    fn ptr_id(&self) -> usize {
        Arc::as_ptr(self) as usize
    }
}

impl AsAnyValue for Arc<String> {
    fn as_any_value(&self) -> AnyValue {
        AnyValue::new(self.clone())
    }
}

// Config

#[derive(Clone)]
pub struct Config {
    runner: AsyncRunner,
}

impl Config {
    pub fn runner(&self) -> &AsyncRunner {
        &self.runner
    }
}

impl Default for Config {
    fn default() -> Self {
        ConfigBuilder::default().build()
    }
}

#[derive(Clone, Default)]
pub struct ConfigBuilder {
    runner: Option<AsyncRunner>,
}

impl ConfigBuilder {
    pub fn build(self) -> Config {
        Config {
            runner: self
                .runner
                .unwrap_or_else(|| SingleThreadAsyncRunner::new()),
        }
    }

    pub fn set_runner(&mut self, runner: AsyncRunner) -> &mut Self {
        self.runner = Some(runner);
        self
    }
}

// DenseBitVector

// TODO Could use bit-vec crate or other, but our current needs are small.
// TODO And we might not want to expose some arbitrary 3rd-party crate for small needs.
#[derive(Clone)]
pub struct DenseBitVector(Arc<RwLock<Vec<BitChunk>>>);
type BitChunk = u32;
const BIT_CHUNK_SIZE: usize = 8 * std::mem::size_of::<BitChunk>();

impl DenseBitVector {
    pub fn with_capacity(capacity: i32) -> DenseBitVector {
        let capacity: usize = capacity.try_into().unwrap();
        let chunk_capacity = (capacity + BIT_CHUNK_SIZE - 1) / BIT_CHUNK_SIZE;
        let bits = Vec::with_capacity(chunk_capacity);
        DenseBitVector(Arc::new(RwLock::new(bits)))
    }

    fn split_index(index: i32) -> (usize, u32) {
        let index: usize = index.try_into().unwrap();
        (index / BIT_CHUNK_SIZE, 1 << (index % BIT_CHUNK_SIZE))
    }

    pub fn get(&self, index: i32) -> bool {
        let (chunk_index, bit) = DenseBitVector::split_index(index);
        let chunks = self.0.read().unwrap();
        match chunks.get(chunk_index) {
            Some(chunk) => (chunk & bit) != 0,
            None => false,
        }
    }

    pub fn set(&self, index: i32, value: bool) {
        let (chunk_index, bit) = DenseBitVector::split_index(index);
        let mut chunks = self.0.write().unwrap();
        if chunk_index >= chunks.len() {
            chunks.resize(chunk_index + 1, 0);
        }
        match value {
            true => chunks[chunk_index] |= bit,
            false => chunks[chunk_index] &= !bit,
        }
    }
}

// Deque

pub type Deque<T> = Arc<RwLock<VecDeque<T>>>;

pub mod deque {
    use super::Deque;
    use std::collections::VecDeque;
    use std::sync::{Arc, RwLock};

    pub fn new<T>() -> Deque<T> {
        Arc::new(RwLock::new(VecDeque::new()))
    }

    pub fn add<T>(deque: &Deque<T>, value: T) {
        deque.write().unwrap().push_back(value);
    }

    pub fn is_empty<T>(deque: &Deque<T>) -> bool {
        deque.read().unwrap().is_empty()
    }

    pub fn remove_first<T>(deque: &Deque<T>) -> T {
        deque.write().unwrap().pop_front().unwrap()
    }
}

// Error

#[derive(Clone, Debug)]
pub struct Error(Option<Arc<dyn std::error::Error + Send + Sync>>);

impl Error {
    pub fn new() -> Self {
        Error(None)
    }

    pub fn with_optional_message(message: Option<Arc<String>>) -> Self {
        Error(message.map(|message| {
            Arc::new(MessageError(message.clone())) as Arc<dyn std::error::Error + Send + Sync>
        }))
    }

    pub fn with_source(source: Arc<dyn std::error::Error + Send + Sync>) -> Self {
        Error(Some(source))
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self}")
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        self.0
            .as_ref()
            .map(|e| e.as_ref() as &(dyn std::error::Error + 'static))
    }
}

#[derive(Debug)]
struct MessageError(Arc<String>);

impl fmt::Display for MessageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for MessageError {}

pub fn i32_to_usize(i: i32) -> Result<usize> {
    i.try_into().map_err(|e| Error::with_source(Arc::new(e)))
}

pub fn usize_to_i32(u: usize) -> Result<i32> {
    u.try_into().map_err(|e| Error::with_source(Arc::new(e)))
}

// Ignore

pub fn ignore<T>(_: T) {}

// Int

pub fn int_div(a: i32, b: i32) -> Result<i32> {
    if b == 0 {
        // We want explicit errors for 0 but not for min / -1.
        return Err(Error::new());
    }
    Ok(a.wrapping_div(b))
}

pub fn int_rem(a: i32, b: i32) -> Result<i32> {
    if b == 0 {
        // We want explicit errors for 0 but not for min % -1.
        return Err(Error::new());
    }
    Ok(a.wrapping_rem(b))
}

pub fn int_to_string(i: i32, radix: Option<i32>) -> Arc<String> {
    int64_to_string(i as i64, radix)
}

// Int64

pub fn int64_div(a: i64, b: i64) -> Result<i64> {
    if b == 0 {
        // We want explicit errors for 0 but not for min / -1.
        return Err(Error::new());
    }
    Ok(a.wrapping_div(b))
}

pub fn int64_rem(a: i64, b: i64) -> Result<i64> {
    if b == 0 {
        // We want explicit errors for 0 but not for min % -1.
        return Err(Error::new());
    }
    Ok(a.wrapping_rem(b))
}

const MANTISSA_MAX_I64: i64 = 1i64 << 53;

pub fn int64_to_float64(i: i64) -> Result<f64> {
    match () {
        _ if i > -MANTISSA_MAX_I64 && i < MANTISSA_MAX_I64 => Ok(i as f64),
        _ => Err(Error::new()),
    }
}

pub fn int64_to_int32(i: i64) -> Result<i32> {
    match () {
        _ if i >= (i32::MIN as i64) && i <= (i32::MAX as i64) => Ok(i as i32),
        _ => Err(Error::new()),
    }
}

pub fn int64_to_string(i: i64, radix: Option<i32>) -> Arc<String> {
    let radix = TryInto::<u64>::try_into(radix.unwrap_or(10)).unwrap();
    let negative = i < 0;
    let mut i = (i as i128).abs() as u64;
    let mut result = vec![];
    loop {
        let value = i % radix;
        i /= radix;
        // Panics on radix outside 2 through 36, which is ok.
        result.push(char::from_digit(value as u32, radix as u32).unwrap() as u8);
        if i == 0 {
            break;
        }
    }
    if negative {
        result.push(b'-');
    }
    result.reverse();
    Arc::new(String::from_utf8(result).unwrap())
}

// Result

pub type Result<T> = std::result::Result<T, Error>;

// String

pub trait ToArcString {
    fn to_arc_string(self) -> Arc<String>;
}

impl ToArcString for Arc<String> {
    fn to_arc_string(self) -> Arc<String> {
        self
    }
}

impl ToArcString for char {
    fn to_arc_string(self) -> Arc<String> {
        Arc::new(self.to_string())
    }
}

impl ToArcString for String {
    fn to_arc_string(self) -> Arc<String> {
        Arc::new(self)
    }
}

impl ToArcString for &str {
    fn to_arc_string(self) -> Arc<String> {
        Arc::new(self.to_string())
    }
}
