package com.wpanther.notification.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonMapConverter Tests")
class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Nested
    @DisplayName("convertToDatabaseColumn() tests")
    class ConvertToDatabaseColumnTests {

        @Test
        @DisplayName("Should convert non-empty map to JSON string")
        void testConvertNonEmptyMapToJson() {
            // Arrange
            Map<String, Object> map = new HashMap<>();
            map.put("key1", "value1");
            map.put("key2", 123);
            map.put("key3", true);

            // Act
            String json = converter.convertToDatabaseColumn(map);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("\"key1\":\"value1\"");
            assertThat(json).contains("\"key2\":123");
            assertThat(json).contains("\"key3\":true");
        }

        @Test
        @DisplayName("Should return null for null map")
        void testReturnNullForNullMap() {
            // Act
            String json = converter.convertToDatabaseColumn(null);

            // Assert
            assertThat(json).isNull();
        }

        @Test
        @DisplayName("Should return null for empty map")
        void testReturnNullForEmptyMap() {
            // Arrange
            Map<String, Object> map = new HashMap<>();

            // Act
            String json = converter.convertToDatabaseColumn(map);

            // Assert
            assertThat(json).isNull();
        }

        @Test
        @DisplayName("Should handle nested maps")
        void testHandleNestedMaps() {
            // Arrange
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("nestedKey", "nestedValue");

            Map<String, Object> map = new HashMap<>();
            map.put("outerKey", nestedMap);

            // Act
            String json = converter.convertToDatabaseColumn(map);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("\"outerKey\"");
            assertThat(json).contains("\"nestedKey\":\"nestedValue\"");
        }

        @Test
        @DisplayName("Should handle list values")
        void testHandleListValues() {
            // Arrange
            Map<String, Object> map = new HashMap<>();
            map.put("items", java.util.List.of("item1", "item2", "item3"));

            // Act
            String json = converter.convertToDatabaseColumn(map);

            // Assert
            assertThat(json).isNotNull();
            assertThat(json).contains("\"items\":[\"item1\",\"item2\",\"item3\"]");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for non-serializable object")
        void testThrowExceptionForNonSerializableObject() {
            // Arrange
            Map<String, Object> map = new HashMap<>();
            map.put("object", new Object() {
                // Non-serializable anonymous class
            });

            // Act & Assert
            assertThatThrownBy(() -> converter.convertToDatabaseColumn(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error serializing map to JSON string");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute() tests")
    class ConvertToEntityAttributeTests {

        @Test
        @DisplayName("Should convert JSON string to map")
        void testConvertJsonStringToMap() {
            // Arrange
            String json = "{\"key1\":\"value1\",\"key2\":123,\"key3\":true}";

            // Act
            Map<String, Object> map = converter.convertToEntityAttribute(json);

            // Assert
            assertThat(map).isNotNull();
            assertThat(map).hasSize(3);
            assertThat(map.get("key1")).isEqualTo("value1");
            assertThat(map.get("key2")).isEqualTo(123);
            assertThat(map.get("key3")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should return empty map for null string")
        void testReturnEmptyMapForNullString() {
            // Act
            Map<String, Object> map = converter.convertToEntityAttribute(null);

            // Assert
            assertThat(map).isNotNull();
            assertThat(map).isEmpty();
        }

        @Test
        @DisplayName("Should return empty map for empty string")
        void testReturnEmptyMapForEmptyString() {
            // Act
            Map<String, Object> map = converter.convertToEntityAttribute("");

            // Assert
            assertThat(map).isNotNull();
            assertThat(map).isEmpty();
        }

        @Test
        @DisplayName("Should handle nested JSON objects")
        void testHandleNestedJsonObjects() {
            // Arrange
            String json = "{\"outerKey\":{\"nestedKey\":\"nestedValue\"}}";

            // Act
            Map<String, Object> map = converter.convertToEntityAttribute(json);

            // Assert
            assertThat(map).isNotNull();
            assertThat(map).containsKey("outerKey");
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) map.get("outerKey");
            assertThat(nestedMap).containsEntry("nestedKey", "nestedValue");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid JSON")
        void testThrowExceptionForInvalidJson() {
            // Arrange
            String invalidJson = "{invalid json}";

            // Act & Assert
            assertThatThrownBy(() -> converter.convertToEntityAttribute(invalidJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error deserializing JSON string to map");
        }
    }

    @Nested
    @DisplayName("Round-trip conversion tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should maintain data integrity through round-trip conversion")
        void testRoundTripConversion() {
            // Arrange
            Map<String, Object> originalMap = new HashMap<>();
            originalMap.put("stringKey", "stringValue");
            originalMap.put("numberKey", 42);
            originalMap.put("booleanKey", false);
            originalMap.put("nullKey", null);

            // Act - convert to database column and back
            String json = converter.convertToDatabaseColumn(originalMap);
            Map<String, Object> restoredMap = converter.convertToEntityAttribute(json);

            // Assert
            assertThat(restoredMap).isEqualTo(originalMap);
        }
    }
}
