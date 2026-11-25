package pe.sevens.sigro.comun;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON con el mismo formato que el servicio original (Pydantic v2): decimales como texto («12.500»), fechas ISO,
 * instantes en UTC con «Z» y microsegundos solo si los hay, y los nulos presentes.
 */
@Configuration
public class Json {
    public static final ObjectMapper M = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .registerModule(modulo());

    private static final DateTimeFormatter SEG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static String instante(Instant i) {
        var t = i.atOffset(ZoneOffset.UTC);
        String base = t.format(SEG);
        int micros = t.getNano() / 1000;
        return micros == 0 ? base + "Z" : base + String.format(".%06d", micros) + "Z";
    }

    static SimpleModule modulo() {
        var m = new SimpleModule("sigro");
        m.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override public void serialize(BigDecimal v, JsonGenerator g, SerializerProvider p) throws IOException { g.writeString(v.toString()); }
        });
        m.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override public void serialize(Instant v, JsonGenerator g, SerializerProvider p) throws IOException { g.writeString(instante(v)); }
        });
        m.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override public void serialize(OffsetDateTime v, JsonGenerator g, SerializerProvider p) throws IOException { g.writeString(instante(v.toInstant())); }
        });
        m.addSerializer(LocalDate.class, new JsonSerializer<>() {
            @Override public void serialize(LocalDate v, JsonGenerator g, SerializerProvider p) throws IOException { g.writeString(v.toString()); }
        });
        m.addSerializer(LocalTime.class, new JsonSerializer<>() {
            @Override public void serialize(LocalTime v, JsonGenerator g, SerializerProvider p) throws IOException { g.writeString(v.toString()); }
        });
        m.addSerializer(Datos.class, new JsonSerializer<>() {
            @Override public void serialize(Datos v, JsonGenerator g, SerializerProvider p) throws IOException { p.defaultSerializeValue(v.valores(), g); }
        });
        return m;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer sigroJackson() {
        return b -> b.modulesToInstall(modulo())
                .featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
    }

    /** JsonNode → Map/List/escalares de Java. */
    public static Object aJava(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isObject()) {
            var m = new LinkedHashMap<String, Object>();
            n.fields().forEachRemaining(e -> m.put(e.getKey(), aJava(e.getValue())));
            return m;
        }
        if (n.isArray()) {
            var l = new ArrayList<Object>();
            n.forEach(x -> l.add(aJava(x)));
            return l;
        }
        if (n.isTextual()) return n.textValue();
        if (n.isBoolean()) return n.booleanValue();
        if (n.isIntegralNumber()) return n.longValue();
        if (n.isNumber()) return n.decimalValue();
        return n.asText();
    }

    public static String texto(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode leer(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNodeFactory nodos() { return JsonNodeFactory.instance; }
}
