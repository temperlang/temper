use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            round_trip(Point::json_adapter(), Point::new(1, -2));
            round_trip(
                Record::json_adapter(),
                Record::new(
                    "Ada",
                    true,
                    1.5,
                    123456789012,
                    None::<Arc<String>>,
                    Arc::new(vec![Point::new(3, 4)]),
                ),
            );
            let shapes: temper_core::List<Shape> = Arc::new(vec![
                Shape::new(Circle::new(2)),
                Shape::new(Rect::new(3, 4)),
            ]);
            round_trip(
                temper_std::json::list_json_adapter(Shape::json_adapter()),
                shapes.clone(),
            );
            let messages: temper_core::List<Message> = Arc::new(vec![
                Message::new(Hello::new("hi")),
                Message::new(Bye::new("so long")),
            ]);
            round_trip(
                temper_std::json::list_json_adapter(Message::json_adapter()),
                messages.clone(),
            );
            round_trip(Celsius::json_adapter(), Celsius::new(21));
            let shape = decode(Shape::json_adapter(), "{\"height\": 6, \"width\": 5}")
                .unwrap_or_else(|_| panic!());
            {
                if temper_core::is::<Rect>(shape.clone()) {
                    println!(
                        "rect {}x{}",
                        temper_core::cast::<Rect>(shape.clone()).unwrap().0.width,
                        temper_core::cast::<Rect>(shape.clone()).unwrap().0.height
                    );
                } else {
                    println!("not a rect");
                }
            }
            let record = decode(
                Record::json_adapter(),
                "{\"name\": \"Bo\", \"is-active\": false, \"score\": 2, \"big\": 7, \"nickname\": \"B\", \"points\": []}",
            )
            .unwrap_or_else(|_| panic!());
            println!(
                "{} {} {} {}",
                record.0.name,
                record.0.active,
                {
                    let subject = record.0.nickname.clone();
                    subject
                        .clone()
                        .unwrap_or_else(|| Arc::new("none".to_string()))
                },
                temper_core::ListedTrait::len(&record.0.points)
            );
            println!(
                "{}",
                (|| {
                    decode(Point::json_adapter(), "{\"x\": 1}")?;
                    Ok::<_, temper_core::Error>(Arc::new("decoded".to_string()))
                })()
                .unwrap_or_else(|_| Arc::new("no y".to_string()))
            );
            println!(
                "{}",
                (|| {
                    decode(
                        Message::json_adapter(),
                        "{\"kind\": \"what\", \"text\": \"?\"}",
                    )?;
                    Ok::<_, temper_core::Error>(Arc::new("decoded".to_string()))
                })()
                .unwrap_or_else(|_| Arc::new("no such kind".to_string()))
            );
            Ok(())
        })
        .clone()
}

struct PointJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct PointJsonAdapter(Arc<PointJsonAdapterStruct>);

impl PointJsonAdapter {
    pub fn new() -> PointJsonAdapter {
        PointJsonAdapter(Arc::new(PointJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Point, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Point> {
        Point::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Point> for PointJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Point> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Point, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Point> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(PointJsonAdapter, [temper_std::json::JsonAdapter<Point>]);

struct PointStruct {
    x: i32,
    y: i32,
}

#[derive(Clone)]
pub(crate) struct Point(Arc<PointStruct>);

impl Point {
    pub fn new(x: i32, y: i32) -> Point {
        Point(Arc::new(PointStruct { x, y }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("x".to_string()));
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.x);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("y".to_string()));
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.y);
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Point> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let x_json =
            temper_core::cast::<temper_std::json::JsonNumeric>(obj.property_value_or_bubble("x")?)
                .ok_or_else(temper_core::Error::new)?;
        let x = temper_std::json::JsonNumericTrait::as_int32(&x_json)?;
        let y_json =
            temper_core::cast::<temper_std::json::JsonNumeric>(obj.property_value_or_bubble("y")?)
                .ok_or_else(temper_core::Error::new)?;
        let y = temper_std::json::JsonNumericTrait::as_int32(&y_json)?;
        Ok(Point::new(x, y))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Point> {
        temper_std::json::JsonAdapter::new(PointJsonAdapter::new())
    }
}

temper_core::impl_any_value_trait!(Point, []);

struct RecordJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct RecordJsonAdapter(Arc<RecordJsonAdapterStruct>);

impl RecordJsonAdapter {
    pub fn new() -> RecordJsonAdapter {
        RecordJsonAdapter(Arc::new(RecordJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Record, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Record> {
        Record::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Record> for RecordJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Record> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Record, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Record> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(RecordJsonAdapter, [temper_std::json::JsonAdapter<Record>]);

struct RecordStruct {
    name: Arc<String>,
    active: bool,
    score: f64,
    big: i64,
    nickname: Option<Arc<String>>,
    points: temper_core::List<Point>,
}

#[derive(Clone)]
pub(crate) struct Record(Arc<RecordStruct>);

impl Record {
    pub fn new(
        name: impl ToArcString,
        active: bool,
        score: f64,
        big: i64,
        nickname: Option<impl ToArcString>,
        points: impl temper_core::ToList<Point>,
    ) -> Record {
        let name = name.to_arc_string();
        let nickname = nickname.map(|x| x.to_arc_string());
        let points = points.to_list();
        Record(Arc::new(RecordStruct {
            name,
            active,
            score,
            big,
            nickname,
            points,
        }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("name".to_string()));
        temper_std::json::JsonProducerTrait::string_value(&p, self.0.name.clone());
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("is-active".to_string()));
        temper_std::json::JsonProducerTrait::boolean_value(&p, self.0.active);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("score".to_string()));
        temper_std::json::JsonProducerTrait::float64_value(&p, self.0.score);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("big".to_string()));
        temper_std::json::JsonProducerTrait::int64_value(&p, self.0.big);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("nickname".to_string()));
        temper_std::json::OrNullJsonAdapter::new(temper_std::json::string_json_adapter())
            .encode_to_json(self.0.nickname.clone(), p.clone());
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("points".to_string()));
        temper_std::json::JsonAdapterTrait::encode_to_json(
            &temper_std::json::list_json_adapter(Point::json_adapter()),
            self.0.points.clone(),
            p.clone(),
        );
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Record> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let name_json =
            temper_core::cast::<temper_std::json::JsonString>(obj.property_value_or_bubble("name")?)
                .ok_or_else(temper_core::Error::new)?;
        let name = name_json.content();
        let active_json = temper_core::cast::<temper_std::json::JsonBoolean>(
            obj.property_value_or_bubble("is-active")?,
        )
        .ok_or_else(temper_core::Error::new)?;
        let active = active_json.content();
        let score_json = temper_core::cast::<temper_std::json::JsonNumeric>(
            obj.property_value_or_bubble("score")?,
        )
        .ok_or_else(temper_core::Error::new)?;
        let score = temper_std::json::JsonNumericTrait::as_float64(&score_json)?;
        let big_json =
            temper_core::cast::<temper_std::json::JsonNumeric>(obj.property_value_or_bubble("big")?)
                .ok_or_else(temper_core::Error::new)?;
        let big = temper_std::json::JsonNumericTrait::as_int64(&big_json)?;
        let nickname =
            temper_std::json::OrNullJsonAdapter::new(temper_std::json::string_json_adapter())
                .decode_from_json(obj.property_value_or_bubble("nickname")?, ic.clone())?;
        let points: temper_core::List<Point> = temper_std::json::JsonAdapterTrait::decode_from_json(
            &temper_std::json::list_json_adapter(Point::json_adapter()),
            obj.property_value_or_bubble("points")?,
            ic.clone(),
        )?;
        Ok(Record::new(
            name.clone(),
            active,
            score,
            big,
            nickname.clone(),
            points.clone(),
        ))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Record> {
        temper_std::json::JsonAdapter::new(RecordJsonAdapter::new())
    }
}

temper_core::impl_any_value_trait!(Record, []);

struct CircleJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct CircleJsonAdapter(Arc<CircleJsonAdapterStruct>);

impl CircleJsonAdapter {
    pub fn new() -> CircleJsonAdapter {
        CircleJsonAdapter(Arc::new(CircleJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Circle, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Circle> {
        Circle::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Circle> for CircleJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Circle> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Circle, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Circle> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(CircleJsonAdapter, [temper_std::json::JsonAdapter<Circle>]);

struct CircleStruct {
    radius: i32,
}

#[derive(Clone)]
pub(crate) struct Circle(Arc<CircleStruct>);

impl Circle {
    pub fn new(radius: i32) -> Circle {
        Circle(Arc::new(CircleStruct { radius }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("radius".to_string()));
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.radius);
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Circle> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let radius_json = temper_core::cast::<temper_std::json::JsonNumeric>(
            obj.property_value_or_bubble("radius")?,
        )
        .ok_or_else(temper_core::Error::new)?;
        let radius = temper_std::json::JsonNumericTrait::as_int32(&radius_json)?;
        Ok(Circle::new(radius))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Circle> {
        temper_std::json::JsonAdapter::new(CircleJsonAdapter::new())
    }
}

impl ShapeTrait for Circle {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        self.encode_to_json(p)
    }
}

temper_core::impl_any_value_trait!(Circle, [Shape]);

struct RectJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct RectJsonAdapter(Arc<RectJsonAdapterStruct>);

impl RectJsonAdapter {
    pub fn new() -> RectJsonAdapter {
        RectJsonAdapter(Arc::new(RectJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Rect, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Rect> {
        Rect::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Rect> for RectJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Rect> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Rect, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Rect> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(RectJsonAdapter, [temper_std::json::JsonAdapter<Rect>]);

struct RectStruct {
    width: i32,
    height: i32,
}

#[derive(Clone)]
pub(crate) struct Rect(Arc<RectStruct>);

impl Rect {
    pub fn new(width: i32, height: i32) -> Rect {
        Rect(Arc::new(RectStruct { width, height }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("width".to_string()));
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.width);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("height".to_string()));
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.height);
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Rect> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let width_json = temper_core::cast::<temper_std::json::JsonNumeric>(
            obj.property_value_or_bubble("width")?,
        )
        .ok_or_else(temper_core::Error::new)?;
        let width = temper_std::json::JsonNumericTrait::as_int32(&width_json)?;
        let height_json = temper_core::cast::<temper_std::json::JsonNumeric>(
            obj.property_value_or_bubble("height")?,
        )
        .ok_or_else(temper_core::Error::new)?;
        let height = temper_std::json::JsonNumericTrait::as_int32(&height_json)?;
        Ok(Rect::new(width, height))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Rect> {
        temper_std::json::JsonAdapter::new(RectJsonAdapter::new())
    }
}

impl ShapeTrait for Rect {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        self.encode_to_json(p)
    }
}

temper_core::impl_any_value_trait!(Rect, [Shape]);

struct ShapeJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct ShapeJsonAdapter(Arc<ShapeJsonAdapterStruct>);

impl ShapeJsonAdapter {
    pub fn new() -> ShapeJsonAdapter {
        ShapeJsonAdapter(Arc::new(ShapeJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Shape, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Shape> {
        Shape::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Shape> for ShapeJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Shape> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Shape, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Shape> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(ShapeJsonAdapter, [temper_std::json::JsonAdapter<Shape>]);

pub(crate) trait ShapeTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Shape;

    fn encode_to_json(&self, p: temper_std::json::JsonProducer);
}

#[derive(Clone)]
pub(crate) struct Shape(Arc<dyn ShapeTrait>);

impl Shape {
    pub fn new(selfish: impl ShapeTrait + 'static) -> Shape {
        Shape(Arc::new(selfish))
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Shape> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let value_for_radius = obj.property_value_or_null("radius");
        {
            if value_for_radius.is_some() {
                return Ok(Shape::new(temper_std::json::JsonAdapterTrait::decode_from_json(
                    &Circle::json_adapter(),
                    temper_std::json::JsonSyntaxTree::new(obj.clone()),
                    ic.clone(),
                )?,));
            }
            if value_for_radius.is_none() {
                return Ok(Shape::new(temper_std::json::JsonAdapterTrait::decode_from_json(
                    &Rect::json_adapter(),
                    temper_std::json::JsonSyntaxTree::new(obj.clone()),
                    ic.clone(),
                )?,));
            }
        }
        Err(temper_core::Error::new())
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Shape> {
        temper_std::json::JsonAdapter::new(ShapeJsonAdapter::new())
    }
}

impl ShapeTrait for Shape {
    fn clone_boxed(&self) -> Shape {
        ShapeTrait::clone_boxed(&*self.0)
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        ShapeTrait::encode_to_json(&*self.0, p)
    }
}

temper_core::impl_any_value_trait_for_interface!(Shape);

impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct HelloJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct HelloJsonAdapter(Arc<HelloJsonAdapterStruct>);

impl HelloJsonAdapter {
    pub fn new() -> HelloJsonAdapter {
        HelloJsonAdapter(Arc::new(HelloJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Hello, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Hello> {
        Hello::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Hello> for HelloJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Hello> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Hello, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Hello> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(HelloJsonAdapter, [temper_std::json::JsonAdapter<Hello>]);

struct HelloStruct {
    text: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Hello(Arc<HelloStruct>);

impl Hello {
    pub fn new(text: impl ToArcString) -> Hello {
        let text = text.to_arc_string();
        Hello(Arc::new(HelloStruct { text }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("text".to_string()));
        temper_std::json::JsonProducerTrait::string_value(&p, self.0.text.clone());
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("kind".to_string()));
        temper_std::json::JsonProducerTrait::string_value(&p, Arc::new("hello".to_string()));
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Hello> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let text_json =
            temper_core::cast::<temper_std::json::JsonString>(obj.property_value_or_bubble("text")?)
                .ok_or_else(temper_core::Error::new)?;
        let text = text_json.content();
        Ok(Hello::new(text.clone()))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Hello> {
        temper_std::json::JsonAdapter::new(HelloJsonAdapter::new())
    }
}

impl MessageTrait for Hello {
    fn clone_boxed(&self) -> Message {
        Message::new(self.clone())
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        self.encode_to_json(p)
    }
}

temper_core::impl_any_value_trait!(Hello, [Message]);

struct ByeJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct ByeJsonAdapter(Arc<ByeJsonAdapterStruct>);

impl ByeJsonAdapter {
    pub fn new() -> ByeJsonAdapter {
        ByeJsonAdapter(Arc::new(ByeJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Bye, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Bye> {
        Bye::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Bye> for ByeJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Bye> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Bye, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Bye> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(ByeJsonAdapter, [temper_std::json::JsonAdapter<Bye>]);

struct ByeStruct {
    text: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Bye(Arc<ByeStruct>);

impl Bye {
    pub fn new(text: impl ToArcString) -> Bye {
        let text = text.to_arc_string();
        Bye(Arc::new(ByeStruct { text }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::start_object(&p);
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("text".to_string()));
        temper_std::json::JsonProducerTrait::string_value(&p, self.0.text.clone());
        temper_std::json::JsonProducerTrait::object_key(&p, Arc::new("kind".to_string()));
        temper_std::json::JsonProducerTrait::string_value(&p, Arc::new("bye".to_string()));
        temper_std::json::JsonProducerTrait::end_object(&p);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Bye> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let text_json =
            temper_core::cast::<temper_std::json::JsonString>(obj.property_value_or_bubble("text")?)
                .ok_or_else(temper_core::Error::new)?;
        let text = text_json.content();
        Ok(Bye::new(text.clone()))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Bye> {
        temper_std::json::JsonAdapter::new(ByeJsonAdapter::new())
    }
}

impl MessageTrait for Bye {
    fn clone_boxed(&self) -> Message {
        Message::new(self.clone())
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        self.encode_to_json(p)
    }
}

temper_core::impl_any_value_trait!(Bye, [Message]);

struct MessageJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct MessageJsonAdapter(Arc<MessageJsonAdapterStruct>);

impl MessageJsonAdapter {
    pub fn new() -> MessageJsonAdapter {
        MessageJsonAdapter(Arc::new(MessageJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Message, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Message> {
        Message::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Message> for MessageJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Message> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Message, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Message> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(MessageJsonAdapter, [temper_std::json::JsonAdapter<Message>]);

pub(crate) trait MessageTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Message;

    fn encode_to_json(&self, p: temper_std::json::JsonProducer);
}

#[derive(Clone)]
pub(crate) struct Message(Arc<dyn MessageTrait>);

impl Message {
    pub fn new(selfish: impl MessageTrait + 'static) -> Message {
        Message(Arc::new(selfish))
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Message> {
        let obj = temper_core::cast::<temper_std::json::JsonObject>(t.clone())
            .ok_or_else(temper_core::Error::new)?;
        let value_for_kind = obj.property_value_or_null("kind");
        {
            if value_for_kind.is_some()
                && temper_core::is::<temper_std::json::JsonString>(value_for_kind.clone().unwrap())
            {
                let subject = temper_core::cast::<temper_std::json::JsonString>(
                    value_for_kind.clone().unwrap(),
                )
                .unwrap()
                .content();
                if subject.as_str() == "hello" {
                    return Ok(Message::new(temper_std::json::JsonAdapterTrait::decode_from_json(
                        &Hello::json_adapter(),
                        temper_std::json::JsonSyntaxTree::new(obj.clone()),
                        ic.clone(),
                    )?,));
                } else if subject.as_str() == "bye" {
                    return Ok(Message::new(temper_std::json::JsonAdapterTrait::decode_from_json(
                        &Bye::json_adapter(),
                        temper_std::json::JsonSyntaxTree::new(obj.clone()),
                        ic.clone(),
                    )?,));
                }
            }
        }
        Err(temper_core::Error::new())
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Message> {
        temper_std::json::JsonAdapter::new(MessageJsonAdapter::new())
    }
}

impl MessageTrait for Message {
    fn clone_boxed(&self) -> Message {
        MessageTrait::clone_boxed(&*self.0)
    }

    fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        MessageTrait::encode_to_json(&*self.0, p)
    }
}

temper_core::impl_any_value_trait_for_interface!(Message);

impl std::ops::Deref for Message {
    type Target = dyn MessageTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct CelsiusJsonAdapterStruct {}

#[derive(Clone)]
pub(crate) struct CelsiusJsonAdapter(Arc<CelsiusJsonAdapterStruct>);

impl CelsiusJsonAdapter {
    pub fn new() -> CelsiusJsonAdapter {
        CelsiusJsonAdapter(Arc::new(CelsiusJsonAdapterStruct {}))
    }

    pub fn encode_to_json(&self, x: Celsius, p: temper_std::json::JsonProducer) {
        x.encode_to_json(p.clone());
    }

    pub fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Celsius> {
        Celsius::decode_from_json(t.clone(), ic.clone())
    }
}

impl temper_std::json::JsonAdapterTrait<Celsius> for CelsiusJsonAdapter {
    fn clone_boxed(&self) -> temper_std::json::JsonAdapter<Celsius> {
        temper_std::json::JsonAdapter::new(self.clone())
    }

    fn encode_to_json(&self, x: Celsius, p: temper_std::json::JsonProducer) {
        self.encode_to_json(x, p)
    }

    fn decode_from_json(
        &self,
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Celsius> {
        self.decode_from_json(t, ic)
    }
}

temper_core::impl_any_value_trait!(CelsiusJsonAdapter, [temper_std::json::JsonAdapter<Celsius>]);

struct CelsiusStruct {
    degrees: i32,
}

#[derive(Clone)]
pub(crate) struct Celsius(Arc<CelsiusStruct>);

impl Celsius {
    pub fn new(degrees: i32) -> Celsius {
        Celsius(Arc::new(CelsiusStruct { degrees }))
    }

    pub fn encode_to_json(&self, p: temper_std::json::JsonProducer) {
        temper_std::json::JsonProducerTrait::int32_value(&p, self.0.degrees);
    }

    pub fn decode_from_json(
        t: temper_std::json::JsonSyntaxTree,
        ic: temper_std::json::InterchangeContext,
    ) -> temper_core::Result<Celsius> {
        Ok(Celsius::new(temper_std::json::JsonNumericTrait::as_int32(
            &temper_core::cast::<temper_std::json::JsonNumeric>(t.clone())
                .ok_or_else(temper_core::Error::new)?,
        )?,))
    }

    pub fn json_adapter() -> temper_std::json::JsonAdapter<Celsius> {
        temper_std::json::JsonAdapter::new(CelsiusJsonAdapter::new())
    }
}

temper_core::impl_any_value_trait!(Celsius, []);

pub(crate) fn encode<T: Clone + Send + Sync + 'static>(
    adapter: temper_std::json::JsonAdapter<T>,
    value: T,
) -> Arc<String> {
    let p = temper_std::json::JsonTextProducer::new(None);
    temper_std::json::JsonAdapterTrait::encode_to_json(
        &adapter,
        value.clone(),
        temper_std::json::JsonProducer::new(p.clone()),
    );
    p.to_json_string()
        .unwrap_or_else(|_| Arc::new("bad JSON".to_string()))
}

pub(crate) fn decode<T: Clone + Send + Sync + 'static>(
    adapter: temper_std::json::JsonAdapter<T>,
    text: impl ToArcString,
) -> temper_core::Result<T> {
    let text = text.to_arc_string();
    temper_std::json::JsonAdapterTrait::decode_from_json(
        &adapter,
        temper_std::json::parse_json(text.clone())?,
        temper_std::json::InterchangeContext::new(
            temper_std::json::NullInterchangeContext::instance(),
        ),
    )
}

pub(crate) fn round_trip<T: Clone + Send + Sync + 'static>(
    adapter: temper_std::json::JsonAdapter<T>,
    value: T,
) {
    let text = encode(adapter.clone(), value.clone());
    let back = decode(adapter.clone(), text.clone()).unwrap_or_else(|_| panic!());
    let again = encode(adapter.clone(), back.clone());
    println!(
        "{}",
        if again == text {
            text.clone()
        } else {
            Arc::new(format!("{text} became {again}"))
        }
    );
}
