package sciens.cyrodracs.appconfig.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppConfigTypeSeederTest {

    @Mock AppConfigTypeRepository typeRepo;
    @Mock AppConfigObjectRepository objectRepo;

    @InjectMocks AppConfigTypeSeeder seeder;

    @Test
    void seedsFourTypesAndRootObjectWhenTablesAreEmpty() {
        AtomicLong idSeq = new AtomicLong(1);
        when(typeRepo.count()).thenReturn(0L);
        when(typeRepo.save(any())).thenAnswer(inv -> {
            AppConfigTypeEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", idSeq.getAndIncrement());
            return e;
        });
        when(objectRepo.count()).thenReturn(0L);

        AppConfigTypeEntity appConfigTypeStub = new AppConfigTypeEntity();
        appConfigTypeStub.setCode("AppConfig");
        ReflectionTestUtils.setField(appConfigTypeStub, "id", 1L);
        when(typeRepo.findByCode("AppConfig")).thenReturn(Optional.of(appConfigTypeStub));

        seeder.seedIfNeeded();

        verify(typeRepo, times(4)).save(any(AppConfigTypeEntity.class));
        verify(objectRepo).save(any(AppConfigObjectEntity.class));
    }

    @Test
    void skipsAllSeedingWhenBothTablesAlreadyPopulated() {
        when(typeRepo.count()).thenReturn(4L);
        when(objectRepo.count()).thenReturn(1L);

        seeder.seedIfNeeded();

        verify(typeRepo, never()).save(any());
        verify(objectRepo, never()).save(any());
    }

    @Test
    void skipsRootObjectSeedingWhenObjectsAlreadyExist() {
        AtomicLong idSeq = new AtomicLong(1);
        when(typeRepo.count()).thenReturn(0L);
        when(typeRepo.save(any())).thenAnswer(inv -> {
            AppConfigTypeEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", idSeq.getAndIncrement());
            return e;
        });
        when(objectRepo.count()).thenReturn(3L);  // objects already exist

        seeder.seedIfNeeded();

        verify(objectRepo, never()).save(any());
    }

    @Test
    void savedTypesHaveCorrectCodes() {
        AtomicLong idSeq = new AtomicLong(1);
        when(typeRepo.count()).thenReturn(0L);
        when(typeRepo.save(any())).thenAnswer(inv -> {
            AppConfigTypeEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", idSeq.getAndIncrement());
            return e;
        });
        when(objectRepo.count()).thenReturn(1L);

        seeder.seedIfNeeded();

        verify(typeRepo).save(argThat(t -> "AppConfig".equals(t.getCode())));
        verify(typeRepo).save(argThat(t -> "DataForm".equals(t.getCode()) && t.isCollection()));
        verify(typeRepo).save(argThat(t -> "DataFormElement".equals(t.getCode()) && t.isCollection()));
        verify(typeRepo).save(argThat(t -> "DataFormElementType".equals(t.getCode()) && t.isEnumType()));
    }
}
