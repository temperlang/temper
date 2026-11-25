use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            {
                let list: temper_core::List<Arc<String>> = Arc::new(vec![
                    Arc::new("[]".to_string()),
                    Arc::new(" [] ".to_string()),
                    Arc::new(" [ ] ".to_string()),
                    Arc::new("[1]".to_string()),
                    Arc::new("null".to_string()),
                ]);
                let length = temper_core::ListedTrait::len(&list);
                let mut i: i32 = 0;
                while i < length {
                    let s = temper_core::ListedTrait::get(&list, i);
                    i = i.wrapping_add(1);
                    let json_value = (|| {
                        Ok::<_, temper_core::Error>(Some(temper_std::json::parse_json(s.clone())?))
                    })()
                    .unwrap_or(None);
                    println!(
                        "`{s}` is empty array: {}",
                        json_value.is_some() && temper_core::is::<temper_std::json::JsonArray>(
                            json_value.clone().unwrap(),
                        ) && temper_core::ListedTrait::is_empty(
                            &temper_core::cast::<temper_std::json::JsonArray>(
                                json_value.clone().unwrap(),
                            )
                            .unwrap()
                            .elements(),
                        )
                    );
                }
            }
            {
                let list2: temper_core::List<Arc<String>> = Arc::new(vec![
                    Arc::new("[".to_string()),
                    Arc::new("]".to_string()),
                    Arc::new("[,]".to_string()),
                    Arc::new("[]]".to_string()),
                    Arc::new("[1,]".to_string()),
                    Arc::new("[1,,2]".to_string()),
                ]);
                let length2 = temper_core::ListedTrait::len(&list2);
                let mut i2: i32 = 0;
                while i2 < length2 {
                    let s2 = temper_core::ListedTrait::get(&list2, i2);
                    i2 = i2.wrapping_add(1);
                    let mut bubbled = false;
                    let p = temper_std::json::JsonSyntaxTreeProducer::new();
                    temper_std::json::parse_json_to_producer(
                        s2.clone(),
                        temper_std::json::JsonProducer::new(p.clone()),
                    );
                    if p.to_json_syntax_tree().is_err() {
                        {
                            bubbled = true;
                        };
                    }
                    let error = p.json_error();
                    println!(
                        "`{s2}` has{} error: {}",
                        if bubbled { "" } else { " no" },
                        error
                            .clone()
                            .unwrap_or_else(|| Arc::new("MISSING ERROR".to_string()))
                    );
                }
            }
            let select_code_points = Arc::new(
                "\0\u{1}\u{2}\u{3}\u{4}\u{5}\u{6}\u{7}\u{8}\t\n\u{b}\u{c}\r\u{e}\u{f}\u{10}\u{11}\u{12}\u{13}\u{14}\u{15}\u{16}\u{17}\u{18}\u{19}\u{1a}\u{1b}\u{1c}\u{1d}\u{1e}\u{1f} -𐀀--퟿  \u{85}"
                    .to_string(),
            );
            {
                let p2 = temper_std::json::JsonTextProducer::new(Some(
                    temper_std::json::InterchangeContext::new(
                        temper_std::json::NullInterchangeContext::instance(),
                    ),
                ));
                p2.string_value(select_code_points.clone());
                println!(
                    "string with select code points: {}",
                    p2.to_json_string()
                        .unwrap_or_else(|_| Arc::new("?!".to_string()))
                );
            }
            {
                let p3 = temper_std::json::JsonTextProducer::new(Some(
                    temper_std::json::InterchangeContext::new(
                        temper_std::json::NullInterchangeContext::instance(),
                    ),
                ));
                p3.start_object();
                let int_or_null_adapter: temper_std::json::OrNullJsonAdapter<i32> =
                    temper_std::json::OrNullJsonAdapter::new(
                        temper_std::json::int32_json_adapter(),
                    );
                let x: Option<i32> = None;
                let y: Option<i32> = Some(123);
                p3.object_key("x");
                int_or_null_adapter
                    .encode_to_json(x, temper_std::json::JsonProducer::new(p3.clone()));
                p3.object_key("y");
                int_or_null_adapter
                    .encode_to_json(y, temper_std::json::JsonProducer::new(p3.clone()));
                p3.end_object();
                println!("{}", p3.to_json_string()?);
            }
            {
                let s3 = Arc::new("{\"x\":null,\"y\":123,\"z\":123456789012}".to_string());
                let ast = temper_std::json::parse_json(s3.clone())?;
                let obj = temper_core::cast::<temper_std::json::JsonObject>(ast.clone()).unwrap();
                let x_ast = obj.property_value_or_bubble("x")?;
                let y_ast = obj.property_value_or_bubble("y")?;
                println!(
                    "for x, got JSON null: {}",
                    temper_core::is::<temper_std::json::JsonNull>(x_ast.clone())
                );
                println!(
                    "for y, got JSON numeric: {}",
                    temper_core::is::<temper_std::json::JsonNumeric>(y_ast.clone())
                );
                let int_or_null_adapter2: temper_std::json::OrNullJsonAdapter<i32> =
                    temper_std::json::OrNullJsonAdapter::new(
                        temper_std::json::int32_json_adapter(),
                    );
                let x2 = int_or_null_adapter2.decode_from_json(
                    x_ast.clone(),
                    temper_std::json::InterchangeContext::new(
                        temper_std::json::NullInterchangeContext::instance(),
                    ),
                )?;
                let y2 = int_or_null_adapter2.decode_from_json(
                    y_ast.clone(),
                    temper_std::json::InterchangeContext::new(
                        temper_std::json::NullInterchangeContext::instance(),
                    ),
                )?;
                println!("x ?? -1 -> {}", x2.unwrap_or(-1));
                println!("y ?? -1 -> {}", y2.unwrap_or(-1));
                let z_ast = obj.property_value_or_bubble("z")?;
                let int64_or_null_adapter: temper_std::json::OrNullJsonAdapter<i64> =
                    temper_std::json::OrNullJsonAdapter::new(
                        temper_std::json::int64_json_adapter(),
                    );
                let z = int64_or_null_adapter.decode_from_json(
                    z_ast.clone(),
                    temper_std::json::InterchangeContext::new(
                        temper_std::json::NullInterchangeContext::instance(),
                    ),
                )?;
                println!("z ?? -1 -> {}", z.unwrap_or(-1));
            }
            let normal_json = temper_std::json::JsonTextProducer::new(None);
            temper_std::json::parse_json_to_producer(
                "{\n  \"foo\": \"bar\",\n  \"baz\": [\n    \"boo\",\n    { \"f\": 1, \"a\": null, \"r\": 123.0 }\n  ]\n}\n",
                temper_std::json::JsonProducer::new(normal_json.clone()),
            );
            println!("Normalized:");
            println!("{}", normal_json.to_json_string()?);
            Ok(())
        })
        .clone()
}
