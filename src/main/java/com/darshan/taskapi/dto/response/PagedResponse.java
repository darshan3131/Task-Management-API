package com.darshan.taskapi.dto.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    public PagedResponse(Page<T> pageData) {
        this.content      = pageData.getContent();
        this.page         = pageData.getNumber();
        this.size         = pageData.getSize();
        this.totalElements = pageData.getTotalElements();
        this.totalPages   = pageData.getTotalPages();
        this.last         = pageData.isLast();
    }
}
