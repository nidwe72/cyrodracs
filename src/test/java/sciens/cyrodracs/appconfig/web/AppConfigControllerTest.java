package sciens.cyrodracs.appconfig.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;
import sciens.cyrodracs.appconfig.service.AppConfigMutationService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AppConfigControllerTest {

    @Mock AppConfigStore store;
    @Mock AppConfigMutationService mutationService;
    @Mock AppConfigTypeRepository typeRepo;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AppConfigController controller = new AppConfigController(store, mutationService, typeRepo);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ---- GET /api/app-config ----

    @Test
    void getAppConfig_returns200_withSerializedTree() throws Exception {
        AppConfig config = buildSampleConfig();
        when(store.getAppConfig()).thenReturn(config);

        mockMvc.perform(get("/api/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("default"))
                .andExpect(jsonPath("$.dataForms.userRegistration").exists());
    }

    @Test
    void getAppConfig_returns404_whenStoreIsEmpty() throws Exception {
        when(store.getAppConfig()).thenReturn(null);

        mockMvc.perform(get("/api/app-config"))
                .andExpect(status().isNotFound());
    }

    // ---- GET /api/app-config/types ----

    @Test
    void getTypes_returnsAllTypeEntities() throws Exception {
        AppConfigTypeEntity t = new AppConfigTypeEntity();
        t.setCode("AppConfig");
        when(typeRepo.findAll()).thenReturn(List.of(t));

        mockMvc.perform(get("/api/app-config/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("AppConfig"));
    }

    // ---- POST /api/app-config/node ----

    @Test
    void addNode_callsMutationServiceAndReturnsUpdatedTree() throws Exception {
        AppConfig updated = buildSampleConfig();
        when(store.getAppConfig()).thenReturn(updated);

        AddNodeRequest request = new AddNodeRequest(1L, "DataForm", "newForm", null);

        mockMvc.perform(post("/api/app-config/node")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("default"));

        verify(mutationService).addNode(any(AddNodeRequest.class));
        verify(store).reload();
    }

    // ---- DELETE /api/app-config/node/{id} ----

    @Test
    void deleteNode_callsMutationServiceAndReturnsUpdatedTree() throws Exception {
        AppConfig updated = buildSampleConfig();
        when(store.getAppConfig()).thenReturn(updated);

        mockMvc.perform(delete("/api/app-config/node/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("default"));

        verify(mutationService).deleteNode(5L);
        verify(store).reload();
    }

    // ---- PATCH /api/app-config/node/{id} ----

    @Test
    void updateNode_callsMutationServiceAndReturnsUpdatedTree() throws Exception {
        AppConfig updated = buildSampleConfig();
        when(store.getAppConfig()).thenReturn(updated);

        UpdateNodeRequest request = new UpdateNodeRequest("renamedForm", null);

        mockMvc.perform(patch("/api/app-config/node/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(mutationService).updateNode(eq(2L), any(UpdateNodeRequest.class));
        verify(store).reload();
    }

    // ---- helper ----

    private AppConfig buildSampleConfig() {
        DataForm form = new DataForm();
        form.setCode("userRegistration");

        AppConfig config = new AppConfig();
        config.setCode("default");
        config.setDataForms(Map.of("userRegistration", form));
        return config;
    }
}
