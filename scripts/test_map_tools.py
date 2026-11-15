import unittest
import subprocess
import sys
import tempfile
from pathlib import Path
from migrate_map_imports import migrate


class MigrationTest(unittest.TestCase):
    def test_rewrites_import_alias_and_initialization_without_changing_body(self):
        source = "import com.google.android.gms.maps.model.LatLng as Point\nval p = Point(1.0, 2.0)\ncom.google.android.gms.maps.MapsInitializer.initialize(context)"
        result, unsupported = migrate(source)
        self.assertIn("com.demo.maps.model.LatLng as Point", result)
        self.assertIn("val p = Point(1.0, 2.0)", result)
        self.assertIn("com.demo.maps.MapsInitializer.initialize", result)
        self.assertFalse(unsupported)

    def test_maps_annotation_and_cluster_aliases_without_changing_body(self):
        source = "import com.google.maps.android.compose.GoogleMapComposable\nimport com.google.maps.android.clustering.Cluster\n@GoogleMapComposable fun marker() = Unit"
        after, unsupported = migrate(source)
        self.assertFalse(unsupported)
        self.assertIn("com.demo.maps.compose.GoogleMapComposable", after)
        self.assertIn("com.demo.maps.clustering.Cluster", after)
        self.assertTrue(after.endswith("@GoogleMapComposable fun marker() = Unit"))

    def test_preserves_comments_and_literals(self):
        source = '// com.google.android.gms.maps.model.LatLng\n/* outer /* nested */ com.google.android.gms.maps.model.LatLng */\nval literal = "com.google.android.gms.maps.model.LatLng"\nval raw = """com.google.android.gms.maps.model.LatLng"""'
        self.assertEqual((source, []), migrate(source))

    def test_flags_native_escape_hatches_and_star_imports(self):
        _, unsupported = migrate("import com.google.maps.android.compose.MapEffect\nimport com.google.android.gms.maps.model.*")
        self.assertEqual(2, len(unsupported))

    def test_does_not_replace_longer_class_names(self):
        source = "import com.google.android.gms.maps.model.MarkerOptions"
        result, unsupported = migrate(source)
        self.assertEqual(source, result)
        self.assertEqual(["com.google.android.gms.maps.model.MarkerOptions"], unsupported)

    def test_maps_cluster_contracts_but_flags_fully_qualified_geometry_calls(self):
        source = ("import com.google.maps.android.clustering.ClusterItem as Item\n"
                  "val distance = com.google.maps.android.SphericalUtil.computeDistanceBetween(a, b)")
        after, unsupported = migrate(source)
        self.assertIn("com.demo.maps.clustering.ClusterItem as Item", after)
        self.assertEqual(["com.google.maps.android.SphericalUtil.computeDistanceBetween"], unsupported)

    def test_apply_rejects_clustering_without_partially_migrating_other_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = {
                "Coordinate.kt": "import com.google.android.gms.maps.model.LatLng\n",
                "Stop.kt": "import com.google.maps.android.clustering.ClusterManager\n",
            }
            for name, source in sources.items():
                (root / name).write_text(source)
            result = subprocess.run(
                [sys.executable, str(Path(__file__).with_name("migrate_map_imports.py")), directory, "--apply"],
                capture_output=True, text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("clustering.ClusterManager", result.stderr)
            for name, source in sources.items():
                self.assertEqual(source, (root / name).read_text())


if __name__ == "__main__":
    unittest.main()
