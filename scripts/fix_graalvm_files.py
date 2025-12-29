import click
import json
from os.path import join
from util import temper_path


def uniq(obj1):
    if isinstance(obj1, dict):
        ret = {}
        for i in sorted(obj1.keys()):
            ret[i] = uniq(obj1[i])
        return ret
    elif isinstance(obj1, list):
        ret = []
        for i in obj1:
            has = False
            for j in ret:
                if i == j:
                    has = True
                    break
            if not has:
                ret.append(i)
        return ret
    else:
        return obj1


def default_paths():
    return [
        temper_path(
            "cli", "src", "main", "resources", "META-INF", "native-image", config
        )
        for config in ("jni-config.json", "reflect-config.json", "resource-config.json")
    ]


@click.command(
    help="Corrects JSON configuration for graalvm.",
    epilog="Specify paths to override the default files to correct.",
)
@click.argument("paths", type=click.Path(exists=True), nargs=-1)
def main(paths):
    for path in paths or default_paths():
        with open(path, "r") as fh:
            data = json.load(fh)
        data = uniq(data)
        with open(path, "w") as fh:
            json.dump(data, fh, indent=2)
