package sciens.cyrodracs.camera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FujiLensMountInsertTest {

    @Autowired CameraProducerRepository producerRepo;
    @Autowired CameraLensMountRepository mountRepo;
    @Autowired CameraLensMount2CameraProducerRepository mappingRepo;

    @Test
    void insertsProducersAndMountsWithMappings() {
        // ── Producers ──
        CameraProducer fuji = findOrCreateProducer("Fuji", YearMonth.of(1934, 1), null);
        CameraProducer zeissIkon = findOrCreateProducer("ZeissIkon", YearMonth.of(1926, 1), YearMonth.of(1972, 1));
        CameraProducer pentax = findOrCreateProducer("Pentax", YearMonth.of(1919, 11), null);
        CameraProducer praktica = findOrCreateProducer("Praktica", YearMonth.of(1919, 1), null);

        // ── Lens Mounts ──
        CameraLensMount m42 = findOrCreateMount("M42", zeissIkon);
        CameraLensMount kMount = findOrCreateMount("K-mount", pentax);
        CameraLensMount xMount = findOrCreateMount("X-Mount", fuji);

        // ── Mappings: which producer uses which mount ──
        // M42 — used by ZeissIkon (creator), Pentax, Praktica, Fuji
        findOrCreateMapping(m42, zeissIkon);
        findOrCreateMapping(m42, pentax);
        findOrCreateMapping(m42, praktica);
        findOrCreateMapping(m42, fuji);

        // K-mount — used by Pentax (creator)
        findOrCreateMapping(kMount, pentax);

        // X-Mount — used by Fuji (creator)
        findOrCreateMapping(xMount, fuji);

        // ── Verify ──
        assertNotNull(fuji.getId());
        assertNotNull(m42.getId());
        assertNotNull(xMount.getId());
        assertEquals("ZeissIkon", m42.getProducer().getName());
        assertEquals("Fuji", xMount.getProducer().getName());
    }

    @Test
    void secondRunCreatesNoDuplicates() {
        long producerCountBefore = producerRepo.count();
        long mountCountBefore = mountRepo.count();
        long mappingCountBefore = mappingRepo.count();

        insertsProducersAndMountsWithMappings();

        assertEquals(producerCountBefore, producerRepo.count());
        assertEquals(mountCountBefore, mountRepo.count());
        assertEquals(mappingCountBefore, mappingRepo.count());
    }

    private CameraProducer findOrCreateProducer(String name, YearMonth foundationYear,
                                                 YearMonth shutdownYear) {
        return producerRepo.findByName(name).orElseGet(() -> {
            CameraProducer p = new CameraProducer();
            p.setName(name);
            p.setFoundationYear(foundationYear);
            p.setShutdownYear(shutdownYear);
            return producerRepo.save(p);
        });
    }

    private CameraLensMount findOrCreateMount(String name, CameraProducer producer) {
        return mountRepo.findByName(name).orElseGet(() -> {
            CameraLensMount m = new CameraLensMount();
            m.setName(name);
            m.setProducer(producer);
            return mountRepo.save(m);
        });
    }

    private CameraLensMount2CameraProducer findOrCreateMapping(CameraLensMount mount,
                                                                CameraProducer producer) {
        return mappingRepo.findByCameraLensMountAndCameraProducer(mount, producer)
                .orElseGet(() -> {
                    CameraLensMount2CameraProducer mapping = new CameraLensMount2CameraProducer();
                    mapping.setCameraLensMount(mount);
                    mapping.setCameraProducer(producer);
                    return mappingRepo.save(mapping);
                });
    }
}
