package sciens.cyrodracs.camera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ErnemannCameraInsertTest {

    @Autowired CameraRepository cameraRepo;
    @Autowired CameraProducerRepository producerRepo;

    private record CameraDef(String name, int releaseYear) {}

    // ---- Ernemann cameras (55 models, ~1891–1925) ----

    private static final List<CameraDef> ERNEMANN_BOB = List.of(
            new CameraDef("Ernemann Bob 0", 1903),
            new CameraDef("Ernemann Bob I", 1903),
            new CameraDef("Ernemann Bob II", 1903),
            new CameraDef("Ernemann Bob III", 1903),
            new CameraDef("Ernemann Bob IV", 1905),
            new CameraDef("Ernemann Bob V", 1905),
            new CameraDef("Ernemann Bob X", 1910),
            new CameraDef("Ernemann Bob XV", 1912),
            new CameraDef("Ernemann Stereo-Bob", 1905),
            new CameraDef("Ernemann Bobette I", 1925),
            new CameraDef("Ernemann Bobette II", 1925)
    );

    private static final List<CameraDef> ERNEMANN_HEAG = List.of(
            new CameraDef("Ernemann Heag 0", 1909),
            new CameraDef("Ernemann Heag I", 1909),
            new CameraDef("Ernemann Heag II", 1909),
            new CameraDef("Ernemann Heag III", 1910),
            new CameraDef("Ernemann Heag IV", 1910),
            new CameraDef("Ernemann Heag V", 1910),
            new CameraDef("Ernemann Heag VI", 1911),
            new CameraDef("Ernemann Heag VII", 1911),
            new CameraDef("Ernemann Heag VIII", 1912),
            new CameraDef("Ernemann Heag IX", 1913),
            new CameraDef("Ernemann Heag X", 1913),
            new CameraDef("Ernemann Heag XI", 1914),
            new CameraDef("Ernemann Heag XII", 1914),
            new CameraDef("Ernemann Heag XIV", 1915),
            new CameraDef("Ernemann Heag XV", 1916),
            new CameraDef("Ernemann Tropen Heag", 1912)
    );

    private static final List<CameraDef> ERNEMANN_OTHER = List.of(
            new CameraDef("Ernemann Ermanox", 1924),
            new CameraDef("Ernemann Klapp", 1903),
            new CameraDef("Ernemann Klapp-Camera", 1903),
            new CameraDef("Ernemann Zwei-Verschluss-Camera", 1910),
            new CameraDef("Ernemann Tropen Klapp", 1910),
            new CameraDef("Ernemann Liliput", 1910),
            new CameraDef("Ernemann Liliput Stereo", 1913),
            new CameraDef("Ernemann Miniature Klapp", 1913),
            new CameraDef("Ernemann Miniature Ernoflex", 1920),
            new CameraDef("Ernemann Unette", 1900),
            new CameraDef("Ernemann Unette Stereo", 1903),
            new CameraDef("Ernemann Film K", 1917),
            new CameraDef("Ernemann Film U", 1919),
            new CameraDef("Ernemann Ernoflex", 1920),
            new CameraDef("Ernemann Ernoflex Folding Reflex", 1921),
            new CameraDef("Ernemann Globus", 1895),
            new CameraDef("Ernemann Globus Stereo", 1897),
            new CameraDef("Ernemann Victoria", 1893),
            new CameraDef("Ernemann Doppel-Sport", 1904),
            new CameraDef("Ernemann Sport", 1904),
            new CameraDef("Ernemann Stereo Ernoflex", 1921),
            new CameraDef("Ernemann Simplex", 1896),
            new CameraDef("Ernemann Simplex Ernoflex", 1922),
            new CameraDef("Ernemann Detectiv", 1891),
            new CameraDef("Ernemann Detectiv-Doppel-Anastigmat", 1900),
            new CameraDef("Ernemann Universal", 1900),
            new CameraDef("Ernemann Rolf I", 1920),
            new CameraDef("Ernemann Rolf II", 1922)
    );

    /** All Ernemann cameras combined. */
    private static final List<CameraDef> ALL_ERNEMANN_CAMERAS;
    static {
        List<CameraDef> all = new ArrayList<>();
        all.addAll(ERNEMANN_BOB);
        all.addAll(ERNEMANN_HEAG);
        all.addAll(ERNEMANN_OTHER);
        ALL_ERNEMANN_CAMERAS = List.copyOf(all);
    }

    private CameraProducer findOrCreateErnemann() {
        return producerRepo.findByName("Ernemann").orElseGet(() -> {
            CameraProducer p = new CameraProducer();
            p.setName("Ernemann");
            p.setFoundationYear(YearMonth.of(1889, 1));
            p.setShutdownYear(YearMonth.of(1926, 1));
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
    void insertsAllErnemannCameras() {
        CameraProducer ernemann = findOrCreateErnemann();

        List<Camera> result = new ArrayList<>();
        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            result.add(insertIfAbsent(def, ernemann));
        }

        assertEquals(ALL_ERNEMANN_CAMERAS.size(), result.size());
        for (Camera c : result) {
            assertNotNull(c.getId());
            assertEquals("Ernemann", c.getProducer().getName());
        }

        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            assertTrue(cameraRepo.findByName(def.name()).isPresent(),
                    def.name() + " must exist in the database");
        }
    }

    @Test
    void secondRunCreatesNoDuplicates() {
        CameraProducer ernemann = findOrCreateErnemann();

        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            insertIfAbsent(def, ernemann);
        }
        long countAfterFirstRun = cameraRepo.count();

        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            insertIfAbsent(def, ernemann);
        }
        long countAfterSecondRun = cameraRepo.count();

        assertEquals(countAfterFirstRun, countAfterSecondRun,
                "Second run must not create duplicates");
    }

    @Test
    void eachCameraHasCorrectReleaseYear() {
        CameraProducer ernemann = findOrCreateErnemann();
        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            insertIfAbsent(def, ernemann);
        }

        for (CameraDef def : ALL_ERNEMANN_CAMERAS) {
            Camera found = cameraRepo.findByName(def.name()).orElseThrow(
                    () -> new AssertionError(def.name() + " not found"));
            assertEquals(YearMonth.of(def.releaseYear(), 1), found.getReleaseYear(),
                    "Release year mismatch for " + def.name());
        }
    }
}
