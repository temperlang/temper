import { type } from "@temperlang/core";

class Ping extends type() {
  /** @type {Pong} */
  #peer;
  /** @type {number} */
  #n;
  /** @type {number} */
  #most;

  /** @param {Pong} peer */
  constructor(peer) {
    super();
    this.#peer = peer;
    this.#n = 0;
    this.#most = 0;
  }

  /** @returns {Pong} */
  get peer() {
    return this.#peer;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @param {number} value */
  set n(value) {
    this.#n = value;
  }

  /** @returns {number} */
  get most() {
    return this.#most;
  }

  /** @param {number} value */
  set most(value) {
    this.#most = value;
  }

  /** @returns {number} */
  ping() {
    this.#peer.pong(this);
    return this.#n;
  }

  /** @returns {number} */
  pass() {
    return this.#peer.bump(this, this.#n);
  }

  /** @returns {boolean} */
  record() {
    this.#n = (this.#n + 1) | 0;
    if (this.#n > this.#most) {
      this.#most = this.#n;
    }
    return this.#n > ((this.#most - 1) | 0);
  }

  /** @returns {number} */
  around() {
    return (((this.#n + this.#peer.bump(this, 10)) | 0) + this.#n) | 0;
  }
}

class Pong extends type() {
  /** @param {Ping} a */
  pong(a) {
    {
      a.n = (a.n + 1) | 0;
    }
  }

  /**
   * @param {Ping} a
   * @param {number} by
   * @returns {number}
   */
  bump(a, by) {
    {
      a.n = (a.n + by) | 0;
    }
    return a.n;
  }
}

const p = new Ping(new Pong());
console.log(`ping=${p.ping()}`);
console.log(`pass=${p.pass()}`);
console.log(`record=${p.record()} most=${p.most}`);
console.log(`around=${p.around()} n=${p.n}`);
