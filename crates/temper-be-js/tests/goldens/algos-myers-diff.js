import {
  denseBitVectorConstructor,
  denseBitVectorGet,
  denseBitVectorSet,
  dequeAdd,
  dequeConstructor,
  dequeGetIsEmpty,
  dequeRemoveFirst,
  listBuilderReverse,
  listBuilderToList,
  listedGet,
  listedSlice,
  requireNotNull,
  stringSplit,
  type,
} from "@temperlang/core";

/**
 * @param {Patch<string>} patch
 * @returns {string}
 */
export function formatPatch(patch) {
  let formatted = "";
  /** @type {readonly Change<string>[]} */
  const changes = patch.changes;
  const n = changes.length;
  {
    let i = 0;
    while (i < n) {
      /** @type {Change<string>} */
      const change = listedGet(/** @type {Change<string>[]} */ (changes), i);
      const changeType = change.type;
      /** @type {readonly string[]} */
      const items = change.items;
      const prefix = changeType === ChangeTypeAddition ? "+" : changeType === ChangeTypeDeletion ? "-" : " ";
      const nItems = items.length;
      {
        let j = 0;
        while (j < nItems) {
          const item = listedGet(/** @type {string[]} */ (items), j);
          formatted = `${formatted}${prefix}${item}\n`;
          j = (j + 1) | 0;
        }
      }
      i = (i + 1) | 0;
    }
  }
  return formatted;
}

/**
 * @template T
 * @param {readonly T[]} left
 * @param {readonly T[]} right
 * @param {(arg0: T, arg1: T) => boolean} eq
 * @returns {Patch<T>}
 */
export function diff(left, right, eq) {
  const leftLength = left.length;
  const rightLength = right.length;
  const reachedRowLength = (leftLength + 1) | 0;
  const reachedColLength = (rightLength + 1) | 0;
  const reached = denseBitVectorConstructor(Math.imul(reachedRowLength, reachedColLength));
  /** @type {(DiffPath | null)[]} */
  const costZeroEdges = dequeConstructor();
  dequeAdd(costZeroEdges, null);
  /** @type {DiffPath[]} */
  const costOneEdges = dequeConstructor();
  while (true) {
    const diffPath = !dequeGetIsEmpty(costZeroEdges) ? dequeRemoveFirst(costZeroEdges) : !dequeGetIsEmpty(costOneEdges) ? dequeRemoveFirst(costOneEdges) : null;
    const leftIndex = (diffPath == null ? null : diffPath.leftIndex) ?? 0;
    const rightIndex = (diffPath == null ? null : diffPath.rightIndex) ?? 0;
    if (leftIndex === leftLength && rightIndex === rightLength) {
      /** @type {DiffPath[]} */
      const pathElements = [];
      {
        let pathElement = diffPath;
        while (pathElement != null) {
          const path = requireNotNull(pathElement);
          pathElements.push(path);
          pathElement = path.previous;
        }
      }
      listBuilderReverse(pathElements);
      let leftPatchIndex = 0;
      let rightPatchIndex = 0;
      /** @type {Change<T>[]} */
      const changes = [];
      const n = pathElements.length;
      {
        let i = 0;
        /** @type {number} */
        let end;
        while (i < n) {
          const changeType = listedGet(pathElements, i).changeType;
          end = (i + 1) | 0;
          while (end < n && changeType === listedGet(pathElements, end).changeType) {
            end = (end + 1) | 0;
          }
          const nItems = (end - i) | 0;
          /** @type {readonly T[]} */
          let items;
          let nLeftItems = 0;
          let nRightItems = 0;
          if (changeType === ChangeTypeAddition) {
            items = listedSlice(/** @type {T[]} */ (right), rightPatchIndex, (rightPatchIndex + nItems) | 0);
            nRightItems = nItems;
          } else {
            items = listedSlice(/** @type {T[]} */ (left), leftPatchIndex, (leftPatchIndex + nItems) | 0);
            if (changeType === ChangeTypeUnchanged) {
              nRightItems = nItems;
            }
            nLeftItems = nItems;
          }
          changes.push(new Change(changeType, leftPatchIndex, rightPatchIndex, items));
          leftPatchIndex = (leftPatchIndex + nLeftItems) | 0;
          rightPatchIndex = (rightPatchIndex + nRightItems) | 0;
          i = end;
        }
      }
      return new Patch(listBuilderToList(changes));
    }
    if (leftIndex < leftLength) {
      if (rightIndex < rightLength) {
        const sameReachedIndex = (((leftIndex + 1) | 0) + Math.imul((rightIndex + 1) | 0, reachedRowLength)) | 0;
        if (!denseBitVectorGet(reached, sameReachedIndex) && eq(listedGet(/** @type {T[]} */ (left), leftIndex), listedGet(/** @type {T[]} */ (right), rightIndex))) {
          denseBitVectorSet(reached, sameReachedIndex, true);
          dequeAdd(costZeroEdges, new DiffPath((leftIndex + 1) | 0, (rightIndex + 1) | 0, ChangeTypeUnchanged, diffPath));
        }
      }
      const delReachedIndex = (((leftIndex + 1) | 0) + Math.imul(rightIndex, reachedRowLength)) | 0;
      if (!denseBitVectorGet(reached, delReachedIndex)) {
        denseBitVectorSet(reached, delReachedIndex, true);
        dequeAdd(costOneEdges, new DiffPath((leftIndex + 1) | 0, rightIndex, ChangeTypeDeletion, diffPath));
      }
    }
    if (rightIndex < rightLength) {
      const addReachedIndex = (leftIndex + Math.imul((rightIndex + 1) | 0, reachedRowLength)) | 0;
      if (!denseBitVectorGet(reached, addReachedIndex)) {
        denseBitVectorSet(reached, addReachedIndex, true);
        dequeAdd(costOneEdges, new DiffPath(leftIndex, (rightIndex + 1) | 0, ChangeTypeAddition, diffPath));
      }
    }
  }
}

/** @template T */
export class Change extends type() {
  /** @type {string} */
  #type;
  /** @type {number} */
  #leftIndex;
  /** @type {number} */
  #rightIndex;
  /** @type {readonly T[]} */
  #items;

  /**
   * @template T
   * @param {{ type: string, leftIndex: number, rightIndex: number, items: readonly T[] }} props
   * @returns {Change<T>}
   */
  static new(props) {
    return new Change(props.type, props.leftIndex, props.rightIndex, props.items);
  }

  /**
   * @param {string} type_
   * @param {number} leftIndex
   * @param {number} rightIndex
   * @param {readonly T[]} items
   */
  constructor(type_, leftIndex, rightIndex, items) {
    super();
    this.#type = type_;
    this.#leftIndex = leftIndex;
    this.#rightIndex = rightIndex;
    this.#items = items;
  }

  /** @returns {string} */
  get type() {
    return this.#type;
  }

  /** @returns {number} */
  get leftIndex() {
    return this.#leftIndex;
  }

  /** @returns {number} */
  get rightIndex() {
    return this.#rightIndex;
  }

  /** @returns {readonly T[]} */
  get items() {
    return this.#items;
  }
}

/** @template T */
export class Patch extends type() {
  /** @type {readonly Change<T>[]} */
  #changes;

  /** @param {readonly Change<T>[]} changes */
  constructor(changes) {
    super();
    this.#changes = changes;
  }

  /** @returns {readonly Change<T>[]} */
  get changes() {
    return this.#changes;
  }
}

class DiffPath extends type() {
  /** @type {number} */
  #leftIndex;
  /** @type {number} */
  #rightIndex;
  /** @type {string} */
  #changeType;
  /** @type {DiffPath | null} */
  #previous;

  /**
   * @param {{ leftIndex: number, rightIndex: number, changeType: string, previous: DiffPath | null }} props
   * @returns {DiffPath}
   */
  static new(props) {
    return new DiffPath(props.leftIndex, props.rightIndex, props.changeType, props.previous);
  }

  /**
   * @param {number} leftIndex
   * @param {number} rightIndex
   * @param {string} changeType
   * @param {DiffPath | null} previous
   */
  constructor(leftIndex, rightIndex, changeType, previous) {
    super();
    this.#leftIndex = leftIndex;
    this.#rightIndex = rightIndex;
    this.#changeType = changeType;
    this.#previous = previous;
  }

  /** @returns {number} */
  get leftIndex() {
    return this.#leftIndex;
  }

  /** @returns {number} */
  get rightIndex() {
    return this.#rightIndex;
  }

  /** @returns {string} */
  get changeType() {
    return this.#changeType;
  }

  /** @returns {DiffPath | null} */
  get previous() {
    return this.#previous;
  }
}

export const ChangeTypeAddition = "Addition";
export const ChangeTypeDeletion = "Deletion";
export const ChangeTypeUnchanged = "Unchanged";
console.log(formatPatch(diff(stringSplit("Hello,\nWorld!", "\n"), stringSplit("Hello,\nCosmos!", "\n"), (a, b) => a === b)));
