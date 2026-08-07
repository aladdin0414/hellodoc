package com.nopkg.hellodoc.web;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 列表数据 */
    private List<T> rows;

    /** 消息状态码 */
    private int code;

    /** 消息内容 */
    private String msg;

    /**
     * 表格数据对象
     */
    public PageResult()
    {
    }

    /**
     * 分页
     * 
     * @param list 列表数据
     * @param total 总记录数
     */
    public PageResult(List<T> list, long total)
    {
        this.rows = list;
        this.total = total;
        this.code = ApiResponse.Code.SUCCESS.code();
        this.msg = ApiResponseMessageBridge.resolveCodeMessage(ApiResponse.Code.SUCCESS);
    }
}
