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
import sciens.cyrodracs.appconfig.web.AddNodeRequest;
import sciens.cyrodracs.appconfig.web.UpdateNodeRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppConfigMutationServiceTest {

    @Mock AppConfigObjectRepository objectRepo;
    @Mock AppConfigTypeRepository typeRepo;

    @InjectMocks AppConfigMutationService service;

    // ---- addNode ----

    @Test
    void addsNodeWithParent() {
        AppConfigTypeEntity type = typeEntity(2L, "DataForm");
        AppConfigObjectEntity parent = objectEntity(1L, null, "default");

        when(typeRepo.findByCode("DataForm")).thenReturn(Optional.of(type));
        when(objectRepo.findById(1L)).thenReturn(Optional.of(parent));
        when(objectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addNode(new AddNodeRequest(1L, "DataForm", "myForm", null));

        verify(objectRepo).save(argThat(e ->
                "DataForm".equals(e.getType().getCode()) &&
                "myForm".equals(e.getCode()) &&
                e.getParentObject() == parent));
    }

    @Test
    void addsRootNodeWithoutParent() {
        AppConfigTypeEntity type = typeEntity(1L, "AppConfig");
        when(typeRepo.findByCode("AppConfig")).thenReturn(Optional.of(type));
        when(objectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addNode(new AddNodeRequest(null, "AppConfig", "default", null));

        verify(objectRepo).save(argThat(e -> e.getParentObject() == null));
    }

    @Test
    void addsEnumNode() {
        AppConfigTypeEntity type = typeEntity(4L, "DataFormElementType");
        AppConfigObjectEntity parent = objectEntity(3L, null, "firstName");

        when(typeRepo.findByCode("DataFormElementType")).thenReturn(Optional.of(type));
        when(objectRepo.findById(3L)).thenReturn(Optional.of(parent));
        when(objectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addNode(new AddNodeRequest(3L, "DataFormElementType", "firstName_type", "INPUT_STRING"));

        verify(objectRepo).save(argThat(e -> "INPUT_STRING".equals(e.getEnumValue())));
    }

    @Test
    void throwsWhenTypeCodeNotFound() {
        when(typeRepo.findByCode("Unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.addNode(new AddNodeRequest(1L, "Unknown", "x", null)));
    }

    @Test
    void throwsWhenParentObjectNotFound() {
        AppConfigTypeEntity type = typeEntity(2L, "DataForm");
        when(typeRepo.findByCode("DataForm")).thenReturn(Optional.of(type));
        when(objectRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.addNode(new AddNodeRequest(99L, "DataForm", "form", null)));
    }

    // ---- deleteNode ----

    @Test
    void deletesLeafNode() {
        AppConfigObjectEntity leaf = objectEntity(5L, null, "leaf");
        when(objectRepo.findById(5L)).thenReturn(Optional.of(leaf));
        when(objectRepo.findByParentObject(leaf)).thenReturn(List.of());

        service.deleteNode(5L);

        verify(objectRepo).delete(leaf);
    }

    @Test
    void deletesNodeAndDescendantsRecursively() {
        AppConfigObjectEntity parent = objectEntity(1L, null, "root");
        AppConfigObjectEntity child  = objectEntity(2L, null, "child");
        AppConfigObjectEntity grand  = objectEntity(3L, null, "grandchild");

        when(objectRepo.findById(1L)).thenReturn(Optional.of(parent));
        when(objectRepo.findByParentObject(parent)).thenReturn(List.of(child));
        when(objectRepo.findByParentObject(child)).thenReturn(List.of(grand));
        when(objectRepo.findByParentObject(grand)).thenReturn(List.of());

        service.deleteNode(1L);

        // Delete order must be leaf-first
        var inOrder = inOrder(objectRepo);
        inOrder.verify(objectRepo).delete(grand);
        inOrder.verify(objectRepo).delete(child);
        inOrder.verify(objectRepo).delete(parent);
    }

    @Test
    void throwsWhenNodeToDeleteNotFound() {
        when(objectRepo.findById(42L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteNode(42L));
    }

    // ---- updateNode ----

    @Test
    void updatesCodeAndEnumValue() {
        AppConfigObjectEntity entity = objectEntity(7L, null, "oldCode");
        entity.setEnumValue("OLD_VALUE");
        when(objectRepo.findById(7L)).thenReturn(Optional.of(entity));
        when(objectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateNode(7L, new UpdateNodeRequest("newCode", "INPUT_STRING"));

        assertEquals("newCode",      entity.getCode());
        assertEquals("INPUT_STRING", entity.getEnumValue());
        verify(objectRepo).save(entity);
    }

    @Test
    void updateIgnoresNullFields() {
        AppConfigObjectEntity entity = objectEntity(8L, null, "original");
        entity.setEnumValue("EXISTING");
        when(objectRepo.findById(8L)).thenReturn(Optional.of(entity));
        when(objectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateNode(8L, new UpdateNodeRequest(null, null));

        assertEquals("original", entity.getCode());
        assertEquals("EXISTING", entity.getEnumValue());
    }

    @Test
    void throwsWhenNodeToUpdateNotFound() {
        when(objectRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateNode(99L, new UpdateNodeRequest("x", null)));
    }

    // ---- helpers ----

    private AppConfigTypeEntity typeEntity(Long id, String code) {
        AppConfigTypeEntity t = new AppConfigTypeEntity();
        ReflectionTestUtils.setField(t, "id", id);
        t.setCode(code);
        return t;
    }

    private AppConfigObjectEntity objectEntity(Long id, AppConfigTypeEntity type, String code) {
        AppConfigObjectEntity e = new AppConfigObjectEntity();
        ReflectionTestUtils.setField(e, "id", id);
        if (type != null) e.setType(type);
        e.setCode(code);
        return e;
    }
}
