package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * Marshaller that makes an attempt at serializing any Java POJO to the "best"
 * JSON representation. For example, iterables should become JSON arrays, Maps should
 * become JSON objects, etc. As a fallback we use {@link Object#toString()}.
 */
public class JsonObjectMarshaller {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final int MAX_LENGTH_LIST = 50;
    private static final int MAX_LENGTH_STRING = 400;

    /**
     * Construct a JsonObjectMarshaller with the default configuration.
     */
    public JsonObjectMarshaller() {

    }

    /**
     * Serialize almost any object to JSON.
     *
     * @param generator JsonGenerator to write object out to.
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public void writeObject(JsonGenerator generator, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            // TODO: handle primitive arrays like ArrayUtils does
            // byte short int long float double char boolean
            /*
            public static Byte[] toObject(final byte[] array) {
                if (array == null) {
                    return null;
                } else if (array.length == 0) {
                    return EMPTY_BYTE_OBJECT_ARRAY;
                }
                final Byte[] result = new Byte[array.length];
                for (int i = 0; i < array.length; i++) {
                    result[i] = Byte.valueOf(array[i]);
                }
                return result;
            }
             */
            value = Arrays.asList((Object[]) value);
        }

        // TODO: handle max recursion
        // TODO: handle cycles
        // default frame allowance of 25
        // default 4k bytes of vars per frame, after that they are silently dropped

        if (value == null) {
            generator.writeNull();
        } else if (value instanceof Path) {
            // Path is weird because it implements Iterable, and then the iterator returns
            // more Paths, which are iterable... which would cause a stack overflow below.
            generator.writeString(value.toString());
        } else if (value instanceof Iterable) {
            // TODO: elide long iterables
            generator.writeStartArray();
            for (Object subValue : (Iterable<?>) value) {
                writeObject(generator, subValue);
            }
            generator.writeEndArray();
        } else if (value instanceof Map) {
            // TODO: elide large maps
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) {
                    generator.writeFieldName("null");
                } else {
                    generator.writeFieldName(entry.getKey().toString());
                }
                writeObject(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else {
            try {
                /** @see com.fasterxml.jackson.core.JsonGenerator#_writeSimpleObject(Object)  */
                generator.writeObject(value);
            } catch (IllegalStateException e) {
                logger.debug("Couldn't marshal '{}' of type '{}', had to be converted into a String",
                    value, value.getClass());
                generator.writeString(value.toString());
            }
        }
    }
}
