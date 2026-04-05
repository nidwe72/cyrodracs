package sciens.cyrodracs.camera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MamiyaCameraInsertTest {

    @Autowired CameraRepository cameraRepo;
    @Autowired CameraProducerRepository producerRepo;

    private record CameraDef(String name, int releaseYear) {}

    // ---- Medium Format SLR — 645 line (13 models) ----
    private static final List<CameraDef> MAMIYA_645 = List.of(
            new CameraDef("Mamiya M645 1000S", 1976),
            new CameraDef("Mamiya M645 J", 1979),
            new CameraDef("Mamiya 645 Super", 1985),
            new CameraDef("Mamiya 645 Pro", 1992),
            new CameraDef("Mamiya 645 Pro TL", 1993),
            new CameraDef("Mamiya 645E", 1994),
            new CameraDef("Mamiya 645 AF", 1999),
            new CameraDef("Mamiya 645 AFD", 2001),
            new CameraDef("Mamiya 645 AFD II", 2004),
            new CameraDef("Mamiya 645 AFD III", 2006),
            new CameraDef("Mamiya 645DF", 2009),
            new CameraDef("Mamiya 645DF+", 2012)
    );

    // ---- Medium Format SLR — RB67 + RZ67 (6 models) ----
    private static final List<CameraDef> MAMIYA_RB_RZ = List.of(
            new CameraDef("Mamiya RB67 Professional", 1970),
            new CameraDef("Mamiya RB67 Pro S", 1974),
            new CameraDef("Mamiya RB67 Pro SD", 1990),
            new CameraDef("Mamiya RZ67 Professional", 1982),
            new CameraDef("Mamiya RZ67 Pro II", 1995),
            new CameraDef("Mamiya RZ67 Pro IID", 2004)
    );

    // ---- Medium Format Rangefinder (4 models) ----
    private static final List<CameraDef> MAMIYA_MF_RANGEFINDER = List.of(
            new CameraDef("Mamiya 6", 1989),
            new CameraDef("Mamiya 6 MF", 1993),
            new CameraDef("Mamiya 7", 1995),
            new CameraDef("Mamiya 7 II", 1999)
    );

    // ---- Medium Format Press (3 models) ----
    private static final List<CameraDef> MAMIYA_PRESS = List.of(
            new CameraDef("Mamiya Press", 1960),
            new CameraDef("Mamiya Press Super 23", 1967),
            new CameraDef("Mamiya Universal Press", 1969)
    );

    // ---- TLR — C series (14 models) ----
    private static final List<CameraDef> MAMIYA_TLR = List.of(
            new CameraDef("Mamiyaflex I", 1948),
            new CameraDef("Mamiyaflex II", 1951),
            new CameraDef("Mamiyaflex Junior", 1953),
            new CameraDef("Mamiyaflex Automat A", 1954),
            new CameraDef("Mamiyaflex Automat B", 1957),
            new CameraDef("Mamiya C2", 1958),
            new CameraDef("Mamiya C3", 1962),
            new CameraDef("Mamiya C22", 1965),
            new CameraDef("Mamiya C33", 1965),
            new CameraDef("Mamiya C220", 1968),
            new CameraDef("Mamiya C330", 1969),
            new CameraDef("Mamiya C220f", 1973),
            new CameraDef("Mamiya C330f", 1973),
            new CameraDef("Mamiya C330S", 1981)
    );

    // ---- 35mm SLR (17 models) ----
    private static final List<CameraDef> MAMIYA_35MM_SLR = List.of(
            new CameraDef("Mamiya Prismat NP", 1961),
            new CameraDef("Mamiya Sekor 500 TL", 1966),
            new CameraDef("Mamiya Sekor 1000 TL", 1966),
            new CameraDef("Mamiya Sekor 500 DTL", 1968),
            new CameraDef("Mamiya Sekor 1000 DTL", 1968),
            new CameraDef("Mamiya Sekor 2000 DTL", 1968),
            new CameraDef("Mamiya MSX 500", 1974),
            new CameraDef("Mamiya MSX 1000", 1974),
            new CameraDef("Mamiya DSX 500", 1974),
            new CameraDef("Mamiya DSX 1000", 1974),
            new CameraDef("Mamiya DSX 1000B", 1975),
            new CameraDef("Mamiya NC1000", 1978),
            new CameraDef("Mamiya NC1000S", 1980),
            new CameraDef("Mamiya ZE", 1980),
            new CameraDef("Mamiya ZE-2", 1982),
            new CameraDef("Mamiya ZE-X", 1983),
            new CameraDef("Mamiya ZM", 1982)
    );

    // ---- 35mm Rangefinder / Compact (10 models) ----
    private static final List<CameraDef> MAMIYA_35MM_RF = List.of(
            new CameraDef("Mamiya 35 I", 1949),
            new CameraDef("Mamiya 35 II", 1952),
            new CameraDef("Mamiya 35 III", 1957),
            new CameraDef("Mamiya 35 S", 1955),
            new CameraDef("Mamiya 35 S2", 1957),
            new CameraDef("Mamiya Elca", 1955),
            new CameraDef("Mamiya Ruby", 1958),
            new CameraDef("Mamiya Family", 1962),
            new CameraDef("Mamiya Magazine 35", 1957),
            new CameraDef("Mamiya Sketch", 1960)
    );

    // ---- Digital Medium Format (10 models) ----
    private static final List<CameraDef> MAMIYA_DIGITAL = List.of(
            new CameraDef("Mamiya ZD", 2004),
            new CameraDef("Mamiya ZD Back", 2006),
            new CameraDef("Mamiya DM22", 2009),
            new CameraDef("Mamiya DM28", 2009),
            new CameraDef("Mamiya DM33", 2009),
            new CameraDef("Mamiya DM40", 2009),
            new CameraDef("Mamiya DM56", 2010),
            new CameraDef("Mamiya Leaf Credo 40", 2011),
            new CameraDef("Mamiya Leaf Credo 60", 2011),
            new CameraDef("Mamiya Leaf Credo 80", 2012)
    );

    /** All Mamiya cameras combined. */
    private static final List<CameraDef> ALL_MAMIYA_CAMERAS;
    static {
        List<CameraDef> all = new ArrayList<>();
        all.addAll(MAMIYA_645);
        all.addAll(MAMIYA_RB_RZ);
        all.addAll(MAMIYA_MF_RANGEFINDER);
        all.addAll(MAMIYA_PRESS);
        all.addAll(MAMIYA_TLR);
        all.addAll(MAMIYA_35MM_SLR);
        all.addAll(MAMIYA_35MM_RF);
        all.addAll(MAMIYA_DIGITAL);
        ALL_MAMIYA_CAMERAS = List.copyOf(all);
    }

    private CameraProducer findOrCreateMamiya() {
        return producerRepo.findByName("Mamiya").orElseGet(() -> {
            CameraProducer p = new CameraProducer();
            p.setName("Mamiya");
            p.setFoundationYear(YearMonth.of(1940, 5));
            return producerRepo.save(p);
        });
    }

    private Camera insertIfAbsent(CameraDef def, CameraProducer producer) {
        return cameraRepo.findByName(def.name()).orElseGet(() -> {
            Camera c = new Camera();
            c.setName(def.name());
            c.setReleaseYear(YearMonth.of(def.releaseYear(), 1));
            c.setProducer(producer);
            return cameraRepo.save(c);
        });
    }

    @Test
    void insertsAllMamiyaCameras() {
        CameraProducer mamiya = findOrCreateMamiya();

        List<Camera> result = new ArrayList<>();
        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            result.add(insertIfAbsent(def, mamiya));
        }

        assertEquals(ALL_MAMIYA_CAMERAS.size(), result.size());
        for (Camera c : result) {
            assertNotNull(c.getId());
            assertEquals("Mamiya", c.getProducer().getName());
        }

        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            assertTrue(cameraRepo.findByName(def.name()).isPresent(),
                    def.name() + " must exist in the database");
        }
    }

    @Test
    void secondRunCreatesNoDuplicates() {
        CameraProducer mamiya = findOrCreateMamiya();

        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            insertIfAbsent(def, mamiya);
        }
        long countAfterFirstRun = cameraRepo.count();

        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            insertIfAbsent(def, mamiya);
        }
        long countAfterSecondRun = cameraRepo.count();

        assertEquals(countAfterFirstRun, countAfterSecondRun,
                "Second run must not create duplicates");
    }

    @Test
    void eachCameraHasCorrectReleaseYear() {
        CameraProducer mamiya = findOrCreateMamiya();
        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            insertIfAbsent(def, mamiya);
        }

        for (CameraDef def : ALL_MAMIYA_CAMERAS) {
            Camera found = cameraRepo.findByName(def.name()).orElseThrow(
                    () -> new AssertionError(def.name() + " not found"));
            assertEquals(YearMonth.of(def.releaseYear(), 1), found.getReleaseYear(),
                    "Release year mismatch for " + def.name());
        }
    }
}
