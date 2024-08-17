package com.xzll.common.pojo.base;

import java.util.Date;

/**
 * @Author: hzz
 * @Date: 2024/8/16 17:23:49
 * @Description:
 */
public class PageModel<T> {
    private static final long serialVersionUID = -1;
    private int currentPage = 1;
    private int pageSize = 15;
    private int count = 0;
    private T body;
    private String pageId;
    private Date pageCreateTime;
    private Integer next;
    private Integer passengerExpireDays;
    private Double commonRatio;

    public String getPageId() {
        return this.pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public Date getPageCreateTime() {
        return this.pageCreateTime;
    }

    public void setPageCreateTime(Date pageCreateTime) {
        this.pageCreateTime = pageCreateTime;
    }

    public Integer getNext() {
        return this.next;
    }

    public void setNext(Integer next) {
        this.next = next;
    }

    public PageModel() {
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public T getBody() {
        return this.body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public Integer getPassengerExpireDays() {
        return this.passengerExpireDays;
    }

    public void setPassengerExpireDays(Integer passengerExpireDays) {
        this.passengerExpireDays = passengerExpireDays;
    }

    public Double getCommonRatio() {
        return this.commonRatio;
    }

    public void setCommonRatio(Double commonRatio) {
        this.commonRatio = commonRatio;
    }

    public int getPageNo() {
        return this.getCurrentPage() <= 0 ? 0 : (this.getCurrentPage() - 1) * this.getPageSize();
    }
}
