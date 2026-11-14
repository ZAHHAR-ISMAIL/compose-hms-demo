"""Guard the user's main requirement: the runnable screen retains the pasted logic verbatim."""
import difflib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKER = "// BEGIN PASTED SCREEN: only map imports and the qualified initializer below differ.\n"


class PastedBusMapTest(unittest.TestCase):
    def test_runnable_screen_matches_the_comparison_copy(self):
        source = (ROOT / 'app/src/main/java/com/demo/hmscomposeapp/busondemand/BusOnDemandRideMap.kt').read_text()
        comparison = (ROOT / 'docs/reference/bus-on-demand/migrated.kt.txt').read_text()
        self.assertEqual(comparison, source.split(MARKER, 1)[1])

    def test_only_twelve_imports_and_one_initializer_reference_change(self):
        folder = ROOT / 'docs/reference/bus-on-demand'
        before = (folder / 'original.kt.txt').read_text()
        after = (folder / 'migrated.kt.txt').read_text()
        self.assertEqual(len(before.splitlines()), len(after.splitlines()))
        changes = [(a, b) for a, b in zip(before.splitlines(), after.splitlines()) if a != b]
        self.assertEqual(13, len(changes))
        self.assertEqual(12, sum(a.startswith('import ') and b.startswith('import ') for a, b in changes))
        body_changes = [(a, b) for a, b in changes if not a.startswith('import ')]
        self.assertEqual([('        com.google.android.gms.maps.MapsInitializer.initialize(context)',
                          '        com.demo.maps.MapsInitializer.initialize(context)')], body_changes)
        patch = ''.join(difflib.unified_diff(before.splitlines(keepends=True), after.splitlines(keepends=True), fromfile='original.kt', tofile='migrated.kt'))
        self.assertEqual(patch, (folder / 'imports-only.diff').read_text())


if __name__ == '__main__':
    unittest.main()
