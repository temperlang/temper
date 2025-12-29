### Support *src* dir

Temper now recognizes a subdir called *src* in project structure if no config
is already in the current dir or higher. Creating a *src* dir is also now the
default behavior of `temper init`. For example:

```bash
~/whatever/blah$ temper init
Initialized new Temper project "blah" in /home/tom/whatever/blah
~/whatever/blah$ find -type f
./src/blah.temper.md
./src/config.temper.md
~/whatever/blah$ temper build
~/whatever/blah$ ls temper.out/js/blah
index.js  index.js.map  package.json
```
