package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.ColumnFilterMeta;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.PickerCandidatesPagedResult;
import sciens.cyrodracs.appconfig.ResolvedDotPathValue;
import sciens.cyrodracs.appconfig.service.ColumnFilterMetadataService;
import sciens.cyrodracs.appconfig.service.PendingDotPathResolverService;
import sciens.cyrodracs.appconfig.service.PickerCandidatesService;
import sciens.cyrodracs.appconfig.service.UserFilterInputs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class ColumnFilterController {

    private final ColumnFilterMetadataService metadataService;
    private final PickerCandidatesService pickerService;
    private final PendingDotPathResolverService dotPathResolver;

    public ColumnFilterController(ColumnFilterMetadataService metadataService,
                                  PickerCandidatesService pickerService,
                                  PendingDotPathResolverService dotPathResolver) {
        this.metadataService = metadataService;
        this.pickerService = pickerService;
        this.dotPathResolver = dotPathResolver;
    }

    @QueryMapping
    public List<ColumnFilterMeta> columnFilterMetadata(@Argument Map<String, Object> scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        String viewNodeCode = (String) scope.get("viewNodeCode");
        String dataFormCode = (String) scope.get("dataFormCode");
        String elementCode = (String) scope.get("elementCode");
        return metadataService.getMetadata(viewNodeCode, dataFormCode, elementCode);
    }

    @QueryMapping
    @SuppressWarnings("unchecked")
    public PickerCandidatesPagedResult entityRefPickerCandidates(@Argument Map<String, Object> input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        Map<String, Object> scope = (Map<String, Object>) input.get("scope");
        if (scope == null) {
            throw new IllegalArgumentException("input.scope is required");
        }
        String viewNodeCode = (String) scope.get("viewNodeCode");
        String dataFormCode = (String) scope.get("dataFormCode");
        String elementCode = (String) scope.get("elementCode");
        String columnKey = (String) input.get("columnKey");
        String term = (String) input.get("term");
        Integer page = (Integer) input.get("page");
        Integer size = (Integer) input.get("size");

        // CF3.4.3 protocol additions — currently plumbed through, consumed in Phase 3.
        Map<String, Object> userFilterInput = (Map<String, Object>) input.get("userFilter");
        FilterNode userFilter = UserFilterInputs.toFilterNode(userFilterInput);
        Number editorEntityIdRaw = (Number) input.get("editorEntityId");
        Long editorEntityId = editorEntityIdRaw == null ? null : editorEntityIdRaw.longValue();

        // CF3.4.4 — pending-row direct field values for picker candidate
        // augmentation in create-new mode. List of { fieldName, ids } tuples
        // keyed by direct field name on the row entity. Null/empty → no
        // augmentation (picker reduces to CF3.4.3 behaviour).
        List<Map<String, Object>> pendingRowDirectValuesRaw =
                (List<Map<String, Object>>) input.get("pendingRowDirectValues");
        Map<String, List<Long>> pendingRowDirectValues = toPendingRowDirectValuesMap(
                pendingRowDirectValuesRaw);

        return pickerService.getCandidates(
                viewNodeCode, dataFormCode, elementCode,
                columnKey, term,
                page != null ? page : 0,
                size != null ? size : 20,
                userFilter,
                editorEntityId,
                pendingRowDirectValues);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Long>> toPendingRowDirectValuesMap(
            List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) return null;
        Map<String, List<Long>> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> entry : raw) {
            String fieldName = (String) entry.get("fieldName");
            List<Number> idsRaw = (List<Number>) entry.get("ids");
            if (fieldName == null || idsRaw == null || idsRaw.isEmpty()) continue;
            List<Long> ids = new java.util.ArrayList<>(idsRaw.size());
            for (Number n : idsRaw) ids.add(n.longValue());
            out.put(fieldName, ids);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * G7.8 — resolves dot-path column values (and rendered display strings)
     * for pending rows on embedded GRIDs in create-new mode.
     */
    @QueryMapping
    @SuppressWarnings("unchecked")
    public List<ResolvedDotPathValue> resolveDotPathValues(@Argument Map<String, Object> input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        String dataFormCode = (String) input.get("dataFormCode");
        String elementCode = (String) input.get("elementCode");

        List<Map<String, Object>> directRaw =
                (List<Map<String, Object>>) input.get("directValues");
        List<PendingDotPathResolverService.DirectFieldValue> directValues = new ArrayList<>();
        if (directRaw != null) {
            for (Map<String, Object> dv : directRaw) {
                Number idxRaw = (Number) dv.get("pendingRowIndex");
                String fieldName = (String) dv.get("fieldName");
                Number idRaw = (Number) dv.get("id");
                if (idxRaw == null || fieldName == null) continue;
                directValues.add(new PendingDotPathResolverService.DirectFieldValue(
                        idxRaw.intValue(),
                        fieldName,
                        idRaw == null ? null : idRaw.longValue()));
            }
        }

        List<Map<String, Object>> colsRaw =
                (List<Map<String, Object>>) input.get("dotPathColumns");
        List<PendingDotPathResolverService.DotPathColumn> dotPathColumns = new ArrayList<>();
        if (colsRaw != null) {
            for (Map<String, Object> col : colsRaw) {
                String columnKey = (String) col.get("columnKey");
                String rendererRef = (String) col.get("rendererRef");
                if (columnKey == null) continue;
                dotPathColumns.add(new PendingDotPathResolverService.DotPathColumn(
                        columnKey, rendererRef));
            }
        }

        return dotPathResolver.resolve(dataFormCode, elementCode, directValues, dotPathColumns);
    }
}
