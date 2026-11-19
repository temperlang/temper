# Java long literals

Long map and list literals of generic objects. Java's `javac` takes time
that grows faster than the entries do to infer type arguments for
diamonds (`new SimpleImmutableEntry<>(…)`) nested in a generic call
(`List.of(…)`): with these two literals of 150 entries, it took seven
minutes and then failed ("inference variable K#1 has incompatible
bounds"). So each literal writes its type arguments
(`List.<Entry<String, Integer>>of`, `new SimpleImmutableEntry<String,
Integer>`), and javac infers nothing nested.

    let words: Map<String, Int> = new Map([
      new Pair("w0", 0), new Pair("w1", 1), new Pair("w2", 2),
      new Pair("w3", 3), new Pair("w4", 4), new Pair("w5", 5),
      new Pair("w6", 6), new Pair("w7", 7), new Pair("w8", 8),
      new Pair("w9", 9), new Pair("w10", 10), new Pair("w11", 11),
      new Pair("w12", 12), new Pair("w13", 13), new Pair("w14", 14),
      new Pair("w15", 15), new Pair("w16", 16), new Pair("w17", 17),
      new Pair("w18", 18), new Pair("w19", 19), new Pair("w20", 20),
      new Pair("w21", 21), new Pair("w22", 22), new Pair("w23", 23),
      new Pair("w24", 24), new Pair("w25", 25), new Pair("w26", 26),
      new Pair("w27", 27), new Pair("w28", 28), new Pair("w29", 29),
      new Pair("w30", 30), new Pair("w31", 31), new Pair("w32", 32),
      new Pair("w33", 33), new Pair("w34", 34), new Pair("w35", 35),
      new Pair("w36", 36), new Pair("w37", 37), new Pair("w38", 38),
      new Pair("w39", 39), new Pair("w40", 40), new Pair("w41", 41),
      new Pair("w42", 42), new Pair("w43", 43), new Pair("w44", 44),
      new Pair("w45", 45), new Pair("w46", 46), new Pair("w47", 47),
      new Pair("w48", 48), new Pair("w49", 49), new Pair("w50", 50),
      new Pair("w51", 51), new Pair("w52", 52), new Pair("w53", 53),
      new Pair("w54", 54), new Pair("w55", 55), new Pair("w56", 56),
      new Pair("w57", 57), new Pair("w58", 58), new Pair("w59", 59),
      new Pair("w60", 60), new Pair("w61", 61), new Pair("w62", 62),
      new Pair("w63", 63), new Pair("w64", 64), new Pair("w65", 65),
      new Pair("w66", 66), new Pair("w67", 67), new Pair("w68", 68),
      new Pair("w69", 69), new Pair("w70", 70), new Pair("w71", 71),
      new Pair("w72", 72), new Pair("w73", 73), new Pair("w74", 74),
      new Pair("w75", 75), new Pair("w76", 76), new Pair("w77", 77),
      new Pair("w78", 78), new Pair("w79", 79), new Pair("w80", 80),
      new Pair("w81", 81), new Pair("w82", 82), new Pair("w83", 83),
      new Pair("w84", 84), new Pair("w85", 85), new Pair("w86", 86),
      new Pair("w87", 87), new Pair("w88", 88), new Pair("w89", 89),
      new Pair("w90", 90), new Pair("w91", 91), new Pair("w92", 92),
      new Pair("w93", 93), new Pair("w94", 94), new Pair("w95", 95),
      new Pair("w96", 96), new Pair("w97", 97), new Pair("w98", 98),
      new Pair("w99", 99), new Pair("w100", 100), new Pair("w101", 101),
      new Pair("w102", 102), new Pair("w103", 103), new Pair("w104",
      104), new Pair("w105", 105), new Pair("w106", 106),
      new Pair("w107", 107), new Pair("w108", 108), new Pair("w109",
      109), new Pair("w110", 110), new Pair("w111", 111),
      new Pair("w112", 112), new Pair("w113", 113), new Pair("w114",
      114), new Pair("w115", 115), new Pair("w116", 116),
      new Pair("w117", 117), new Pair("w118", 118), new Pair("w119",
      119), new Pair("w120", 120), new Pair("w121", 121),
      new Pair("w122", 122), new Pair("w123", 123), new Pair("w124",
      124), new Pair("w125", 125), new Pair("w126", 126),
      new Pair("w127", 127), new Pair("w128", 128), new Pair("w129",
      129), new Pair("w130", 130), new Pair("w131", 131),
      new Pair("w132", 132), new Pair("w133", 133), new Pair("w134",
      134), new Pair("w135", 135), new Pair("w136", 136),
      new Pair("w137", 137), new Pair("w138", 138), new Pair("w139",
      139), new Pair("w140", 140), new Pair("w141", 141),
      new Pair("w142", 142), new Pair("w143", 143), new Pair("w144",
      144), new Pair("w145", 145), new Pair("w146", 146),
      new Pair("w147", 147), new Pair("w148", 148), new Pair("w149",
      149)
    ]);
    let pairs: List<Pair<String, Int>> = [
      new Pair("p0", 0), new Pair("p1", 1), new Pair("p2", 2),
      new Pair("p3", 3), new Pair("p4", 4), new Pair("p5", 5),
      new Pair("p6", 6), new Pair("p7", 7), new Pair("p8", 8),
      new Pair("p9", 9), new Pair("p10", 10), new Pair("p11", 11),
      new Pair("p12", 12), new Pair("p13", 13), new Pair("p14", 14),
      new Pair("p15", 15), new Pair("p16", 16), new Pair("p17", 17),
      new Pair("p18", 18), new Pair("p19", 19), new Pair("p20", 20),
      new Pair("p21", 21), new Pair("p22", 22), new Pair("p23", 23),
      new Pair("p24", 24), new Pair("p25", 25), new Pair("p26", 26),
      new Pair("p27", 27), new Pair("p28", 28), new Pair("p29", 29),
      new Pair("p30", 30), new Pair("p31", 31), new Pair("p32", 32),
      new Pair("p33", 33), new Pair("p34", 34), new Pair("p35", 35),
      new Pair("p36", 36), new Pair("p37", 37), new Pair("p38", 38),
      new Pair("p39", 39), new Pair("p40", 40), new Pair("p41", 41),
      new Pair("p42", 42), new Pair("p43", 43), new Pair("p44", 44),
      new Pair("p45", 45), new Pair("p46", 46), new Pair("p47", 47),
      new Pair("p48", 48), new Pair("p49", 49), new Pair("p50", 50),
      new Pair("p51", 51), new Pair("p52", 52), new Pair("p53", 53),
      new Pair("p54", 54), new Pair("p55", 55), new Pair("p56", 56),
      new Pair("p57", 57), new Pair("p58", 58), new Pair("p59", 59),
      new Pair("p60", 60), new Pair("p61", 61), new Pair("p62", 62),
      new Pair("p63", 63), new Pair("p64", 64), new Pair("p65", 65),
      new Pair("p66", 66), new Pair("p67", 67), new Pair("p68", 68),
      new Pair("p69", 69), new Pair("p70", 70), new Pair("p71", 71),
      new Pair("p72", 72), new Pair("p73", 73), new Pair("p74", 74),
      new Pair("p75", 75), new Pair("p76", 76), new Pair("p77", 77),
      new Pair("p78", 78), new Pair("p79", 79), new Pair("p80", 80),
      new Pair("p81", 81), new Pair("p82", 82), new Pair("p83", 83),
      new Pair("p84", 84), new Pair("p85", 85), new Pair("p86", 86),
      new Pair("p87", 87), new Pair("p88", 88), new Pair("p89", 89),
      new Pair("p90", 90), new Pair("p91", 91), new Pair("p92", 92),
      new Pair("p93", 93), new Pair("p94", 94), new Pair("p95", 95),
      new Pair("p96", 96), new Pair("p97", 97), new Pair("p98", 98),
      new Pair("p99", 99), new Pair("p100", 100), new Pair("p101", 101),
      new Pair("p102", 102), new Pair("p103", 103), new Pair("p104",
      104), new Pair("p105", 105), new Pair("p106", 106),
      new Pair("p107", 107), new Pair("p108", 108), new Pair("p109",
      109), new Pair("p110", 110), new Pair("p111", 111),
      new Pair("p112", 112), new Pair("p113", 113), new Pair("p114",
      114), new Pair("p115", 115), new Pair("p116", 116),
      new Pair("p117", 117), new Pair("p118", 118), new Pair("p119",
      119), new Pair("p120", 120), new Pair("p121", 121),
      new Pair("p122", 122), new Pair("p123", 123), new Pair("p124",
      124), new Pair("p125", 125), new Pair("p126", 126),
      new Pair("p127", 127), new Pair("p128", 128), new Pair("p129",
      129), new Pair("p130", 130), new Pair("p131", 131),
      new Pair("p132", 132), new Pair("p133", 133), new Pair("p134",
      134), new Pair("p135", 135), new Pair("p136", 136),
      new Pair("p137", 137), new Pair("p138", 138), new Pair("p139",
      139), new Pair("p140", 140), new Pair("p141", 141),
      new Pair("p142", 142), new Pair("p143", 143), new Pair("p144",
      144), new Pair("p145", 145), new Pair("p146", 146),
      new Pair("p147", 147), new Pair("p148", 148), new Pair("p149",
      149)
    ];
    let nested: List<List<Pair<String, Int>>> = [pairs, [new Pair("x", 1)]];
    let one<T>(x: T): List<T> { [x] }
    console.log(words.getOr("w149", -1).toString());
    console.log(pairs[149].key);
    console.log(nested[1][0].value.toString());
    console.log(one("only")[0]);

```log
149
p149
1
only
```
