import unittest
from migrate_map_imports import migrate


class MigrationTest(unittest.TestCase):
    def test_rewrites_import_alias_and_initialization_without_changing_body(self):
        source = "import com.google.android.gms.maps.model.LatLng as Point\nval p = Point(1.0, 2.0)\ncom.google.android.gms.maps.MapsInitializer.initialize(context)"
        result, unsupported = migrate(source)
        self.assertIn("com.demo.maps.model.LatLng as Point", result)
        self.assertIn("val p = Point(1.0, 2.0)", result)
        self.assertIn("com.demo.maps.MapsInitializer.initialize", result)
        self.assertFalse(unsupported)

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


if __name__ == "__main__":
    unittest.main()
