package com.commute.metrosync.dto;

import java.util.List;

/**
 * Paged Result for Jakarta EE
 * Replacement for Spring Data's Page interface
 */
public class PagedResult<T> {
    
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
    
    public PagedResult() {
    }
    
    public PagedResult(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
        this.first = page == 0;
        this.last = page >= (totalPages - 1);
        this.empty = content.isEmpty();
    }
    
    public static <T> PagedResult<T> of(List<T> content, int page, int size, long totalElements) {
        return new PagedResult<>(content, page, size, totalElements);
    }
    
    public static <T> PagedResult<T> empty() {
        return new PagedResult<>(List.of(), 0, 0, 0);
    }
    
    // Getters and setters
    
    public List<T> getContent() {
        return content;
    }
    
    public void setContent(List<T> content) {
        this.content = content;
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    public boolean isFirst() {
        return first;
    }
    
    public void setFirst(boolean first) {
        this.first = first;
    }
    
    public boolean isLast() {
        return last;
    }
    
    public void setLast(boolean last) {
        this.last = last;
    }
    
    public boolean isEmpty() {
        return empty;
    }
    
    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
    
    public int getNumberOfElements() {
        return content != null ? content.size() : 0;
    }
    
    public boolean hasContent() {
        return !empty;
    }
    
    public boolean hasNext() {
        return !last;
    }
    
    public boolean hasPrevious() {
        return !first;
    }
}