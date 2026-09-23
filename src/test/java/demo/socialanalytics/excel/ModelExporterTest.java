package demo.socialanalytics.excel;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.support.ExcelTestFiles;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelExporterTest {
    private final ModelExporter exporter = new ModelExporter(new ExcelMapper());

    record SimpleRecord(Long id, String name, Integer score, LocalDateTime createdAt) {
    }

    @Getter
    @Setter
    static class Bean {
        private Long id;
        private String title;
        private boolean active;
        private Platform platform;
    }

    static class FieldOnly {
        private final String code;
        private final int amount;

        FieldOnly(String code, int amount) {
            this.code = code;
            this.amount = amount;
        }
    }

    record Annotated(
        @ExcelColumn(header = "Mã số", order = 2) String code,
        @ExcelColumn(header = "Tên gọi", order = 1) String name
    ) {
    }

    static class NoProperties {
    }

    static class Exploding {
        private String value = "x";

        public String getValue() {
            throw new IllegalStateException("không nạp được dữ liệu");
        }
    }

    @Nested
    class SuyRaCot {
        @Test
        void recordGiuNguyenThuTuKhaiBao() {
            assertThat(exporter.headers(SimpleRecord.class))
                .containsExactly("Id", "Name", "Score", "Created at");
        }

        @Test
        void lopThuongLayTheoThuTuField() {
            assertThat(exporter.headers(Bean.class))
                .containsExactly("Id", "Title", "Active", "Platform");
        }

        @Test
        void doiCamelCaseThanhTieuDeDocDuoc() {
            assertThat(ModelIntrospector.toHeader("externalId")).isEqualTo("External id");
            assertThat(ModelIntrospector.toHeader("id")).isEqualTo("Id");
            assertThat(ModelIntrospector.toHeader("succeededPosts")).isEqualTo("Succeeded posts");
        }

        @Test
        void coExcelColumnThiTonTrongKhaiBao() {
            assertThat(exporter.headers(Annotated.class))
                .containsExactly("Tên gọi", "Mã số");
        }

        @Test
        void quetMotLanRoiDungLai() {
            assertThat(ModelIntrospector.describe(SimpleRecord.class))
                .isSameAs(ModelIntrospector.describe(SimpleRecord.class));
        }

        @Test
        void lopKhongCoThuocTinhNaoThiBaoLoi() {
            assertThatThrownBy(() -> exporter.headers(NoProperties.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không có thuộc tính nào");
        }
    }

    @Nested
    class XuatFile {
        @Test
        void xuatDuocRecordKhongCanAnnotation() {
            var rows = List.of(
                new SimpleRecord(1L, "Một", 10, LocalDateTime.of(2026, 9, 23, 8, 0)),
                new SimpleRecord(2L, "Hai", 20, LocalDateTime.of(2026, 9, 23, 9, 0)));

            var sheet = ExcelTestFiles.readAll(exporter.export(rows, SimpleRecord.class, "Test"));

            assertThat(sheet.get(0)).containsExactly("Id", "Name", "Score", "Created at");
            assertThat(sheet.get(1)).contains("1", "Một", "10");
            assertThat(sheet).hasSize(3);
        }

        @Test
        void goiGetterChuKhongDocThangField() {
            Bean bean = new Bean();
            bean.setId(7L);
            bean.setTitle("Tiêu đề");
            bean.setActive(true);
            bean.setPlatform(Platform.FACEBOOK);

            var sheet = ExcelTestFiles.readAll(exporter.export(List.of(bean), Bean.class, "Test"));

            assertThat(sheet.get(1).get(0)).isEqualTo("7");
            assertThat(sheet.get(1).get(1)).isEqualTo("Tiêu đề");
            assertThat(sheet.get(1).get(2)).isEqualTo("TRUE");
            assertThat(sheet.get(1).get(3)).isEqualTo("facebook");
        }

        // boolean dùng isXxx() chứ không phải getXxx().
        @Test
        void nhanRaGetterKieuIsChoBoolean() {
            assertThat(ModelIntrospector.describe(Bean.class))
                .filteredOn(property -> property.name().equals("active"))
                .singleElement()
                .satisfies(property -> assertThat(property.getter().getName()).isEqualTo("isActive"));
        }

        @Test
        void khongCoGetterThiDocThangField() {
            var sheet = ExcelTestFiles.readAll(
                exporter.export(List.of(new FieldOnly("ABC", 42)), FieldOnly.class, "Test"));

            assertThat(sheet.get(0)).containsExactly("Code", "Amount");
            assertThat(sheet.get(1)).containsExactly("ABC", "42");
        }

        @Test
        void danhSachRongVanCoDongTieuDe() {
            var sheet = ExcelTestFiles.readAll(exporter.export(List.of(), SimpleRecord.class, "Test"));

            assertThat(sheet).hasSize(1);
            assertThat(sheet.get(0)).first().isEqualTo("Id");
        }

        @Test
        void oNullThiDeTrong() {
            var rows = java.util.Collections.singletonList(
                new SimpleRecord(1L, null, null, null));

            var sheet = ExcelTestFiles.readAll(exporter.export(rows, SimpleRecord.class, "Test"));

            assertThat(sheet.get(1).subList(1, 4)).containsOnly("");
        }

        @Test
        void coExcelColumnThiXuatTheoThuTuOrder() {
            var sheet = ExcelTestFiles.readAll(
                exporter.export(List.of(new Annotated("A1", "Tên")), Annotated.class, "Test"));

            assertThat(sheet.get(0)).containsExactly("Tên gọi", "Mã số");
            assertThat(sheet.get(1)).containsExactly("Tên", "A1");
        }

        @Test
        void getterNemLoiThiNoiRoThuocTinh() {
            assertThatThrownBy(() ->
                exporter.export(List.of(new Exploding()), Exploding.class, "Test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("value");
        }
    }

    record WithOddTypes(String name, LocalDate date, List<String> tags, java.math.BigDecimal rate) {
    }

    @Test
    void kieuLaVanXuatDuoc() {
        var rows = List.of(new WithOddTypes(
            "x", LocalDate.of(2026, 9, 23), List.of("a", "b"), new java.math.BigDecimal("25400.5")));

        var sheet = ExcelTestFiles.readAll(exporter.export(rows, WithOddTypes.class, "Test"));

        assertThat(sheet.get(1).get(2)).isEqualTo("2 mục");
        assertThat(sheet.get(1).get(3)).contains("25400");
    }
}
