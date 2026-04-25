package sciens.cyrodracs.appconfig;

import java.util.List;

public class PickerCandidatesPagedResult {

    private List<PickerCandidate> items;
    private long totalCount;
    private int page;
    private int pageSize;

    public PickerCandidatesPagedResult() {}

    public PickerCandidatesPagedResult(List<PickerCandidate> items, long totalCount,
                                       int page, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<PickerCandidate> getItems() { return items; }
    public void setItems(List<PickerCandidate> items) { this.items = items; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getTotalPages() {
        return pageSize == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
    }
}
