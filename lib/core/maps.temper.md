# Maps

Key-value collections: `Pair`, the read-only view `Mapped`, the read-only
`Map`, and the read-write `MapBuilder`.

## Pair

*Pair* holds a *key* and its *value*, as a map's entries do.

    export class Pair<@out K, @out V> {
      public key: K;
      public value: V;

      @connected
      public constructor(key: K, value: V) {
        this.key = key;
        this.value = value;
      }
    }

## Mapped

*Mapped* is a read-only view to key-value mapped data, such as a `Map` or
`MapBuilder`.

    export sealed interface Mapped<@in K extends MapKey<K>, V> {
      /**
       * The number of key value pairs in the [snippet/type/Mapped].
       */
      @connected
      public get length(): Int {
        toListBuilder().length
      }

      /**
       * Get the value associated with [key], or [snippet/builtin/bubble]s if no key is found.
       */
      @connected
      public get(key: K): V throws Bubble {
        var values = new ListBuilder<V>();
        forEach { (k: K, v: V): Void =>
          if (key == k) {
            values.add(v);
          }
        }
        if (values.length != 1) {
          bubble();
        }
        return values[0];
      }

      /**
       * Get the value associated with [key], falling back to [fallback] when no such key exists.
       */
      @connected
      public getOr(key: K, fallback: V): V {
        // TODO: Whether `get` should panic on a missing key, rather than
        // bubble, is open (temperlang/temper#164).
        return this[key] orelse fallback;
      }

      /**
       * Checks whether [key] is present in the [snippet/type/Mapped].
       */
      @connected
      public has(key: K): Boolean {
        return do { this[key]; true } orelse false;
      }

      /**
       * Gets a [snippet/type/Listed] of all of the keys.
       */
      @connected
      public keys(): Listed<K> {
        var all = new ListBuilder<K>();
        forEach { key, value =>
          all.add(key);
        }
        return all;
      }

      /**
       * Gets a [snippet/type/Listed] of all of the values.
       */
      @connected
      public values(): Listed<V> {
        var all = new ListBuilder<V>();
        forEach { key, value =>
          all.add(value);
        }
        return all;
      }

      /**
       * Converts the [snippet/type/Mapped] to a [snippet/type/Map].
       *
       * It does not need to make a copy if the [snippet/type/Mapped] is already a [snippet/type/Map]
       * because neither can be mutated.
       */
      @connected
      public toMap(): Map<K, V> {
        new Map<K, V>(toList())
      }

      /**
       * Converts the [snippet/type/Mapped] to a [snippet/type/MapBuilder].
       *
       * It must make a copy, even when the [snippet/type/Mapped] is already a [snippet/type/MapBuilder]
       * so that both can be mutated independently.
       */
      @connected
      public toMapBuilder(): MapBuilder<K, V> {
        var builder = new MapBuilder<K, V>();
        forEach { key, value =>
          builder[key] = value;
        };
        return builder;
      }

      /**
       * Identical to [snippet/type/Mapped/method/toListBuilder] except for the return value being a [snippet/type/List].
       *
       * Constructs a [Pair][snippet/type/Pair]<[snippet/type/Mapped/typeFormal/K], [snippet/type/Mapped/typeFormal/V]>
       * for each key and value in the [snippet/type/Mapped].
       */
      @connected
      public toList(): List<Pair<K, V>> {
        toListBuilder().toList()
      }

      /**
       * Constructs a [Pair][snippet/type/Pair]<[snippet/type/Mapped/typeFormal/K], [snippet/type/Mapped/typeFormal/V]>
       * for each key and value in the [snippet/type/Mapped].
       */
      @connected
      public toListBuilder(): ListBuilder<Pair<K, V>> {
        mappedToListBuilderWith(this) { (key: K, value: V): Pair<K, V> =>
          new Pair(key, value)
        }
      }

      /**
       * Calls the provided binary function func on each key and value for the side effect
       *
       * This method is more likely to change or be removed before Temper 1.0 than most
       */
      @connected
      public forEach(func: fn(K, V): Void): Void;
    }

    // Mapped's generic methods are extensions, as Listed's are.

## Mapped extensions

`mappedToListWith` (`mapped.toListWith(…)`) is similar to [snippet/type/Mapped/method/toListBuilderWith].

Calls [func] on each key and value in the [snippet/type/Mapped].

    @extension("toListWith")
    @connected
    export let mappedToListWith<K extends MapKey<K>, V, T>(mapped: Mapped<K, V>, func: fn(K, V): T): List<T> {
      mappedToListBuilderWith(mapped, func).toList()
    }

`mappedToListBuilderWith` (`mapped.toListBuilderWith(…)`) returns a [snippet/type/ListBuilder]<*T*>.

Calls [func] on each key and value in the [snippet/type/Mapped].

    @extension("toListBuilderWith")
    @connected
    export let mappedToListBuilderWith<K extends MapKey<K>, V, T>(mapped: Mapped<K, V>, func: fn(K, V): T): ListBuilder<T> {
      var builder = new ListBuilder<T>();
      mapped.forEach { key, value =>
        builder.add(func(key, value));
      }
      return builder;
    }

## Map

*Map* is a read-only key-value collection.

    @connected
    @partialImu
    @mayDowncastTo(false) // Just use core.type Mapped.toMap() if immutable wanted.
    export class Map<@in K extends MapKey<K>, @out V> extends Mapped<K, V> {
      // The content is used only from extension functions.
      private content: AnyValue;

      /**
       * Preserves entry order when accessed from Temper code.
       */
      @connected
      public constructor(entries: List<Pair<K, V>>);
    }

## MapBuilder

*MapBuilder* is a read-write key-value collection for building `Map`
objects. Preserves insertion order when accessed from Temper code.

    @connected
    @mayDowncastTo(false) // Provides a bit of protection at least within Temper.
    export class MapBuilder<K extends MapKey<K>, V> extends Mapped<K, V> {
      // The content is used only from connected functions.
      private content: AnyValue;

      @connected
      public constructor();

      /** Removes all entries, retaining internal capacity where possible. */
      @connected
      public clear(): Void;

      /** Removes the given entry if found, and returns its value. */
      @connected
      public remove(key: K): V throws Bubble;

      @connected
      public set(key: K, newValue: V): Void;
    }
