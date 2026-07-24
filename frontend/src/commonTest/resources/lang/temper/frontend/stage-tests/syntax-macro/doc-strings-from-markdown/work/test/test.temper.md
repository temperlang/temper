# Geometry

Point represents a two-dimensional point.

    class Point(

Point's factory takes two coordinates.  TODO: another factory for polar form.

x is the x coordinate.

      public x: Float64,

y is the y coordinate.

      public y: Float64,
    ) {

magnitude is the distance of this point from the origin.

It is always >= 0.

      magnitude(): Float64 { (x * x + y * y).sqrt() }

    }
