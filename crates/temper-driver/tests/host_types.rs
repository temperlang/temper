//! Each core type a backend translates as a host type or its runtime's
//! own (`Backend::host_types`) is `@connected` in core, as core says
//! where it reads its declarations (`temper_frontend::typing::core_shape`),
//! so `is` and `as` can't target one that several Temper types may share
//! the host type of (notes/core-capabilities.md). Backends are those the
//! registry has; the frontend lists none.

use temper_driver::Registry;
use temper_frontend::typing::core_shape;

#[test]
fn host_types_are_connected_in_core() {
    let mut wrong = Vec::new();
    for backend in Registry::built_in().all() {
        for &name in backend.host_types {
            match core_shape(name) {
                Some(shape) if shape.connected => {}
                Some(_) => wrong.push(format!("{name} ({}): not @connected", backend.name)),
                None => wrong.push(format!(
                    "{name} ({}): not a type core exports",
                    backend.name
                )),
            }
        }
    }
    assert_eq!(wrong, Vec::<String>::new());
}
