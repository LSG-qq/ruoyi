package com.ruoyi.system.domain.vo;

/**
 * 图书类目展示对象（终端囚犯可见字段）
 *
 * <p>终端只在图书列表上方提供一个类目筛选项，需要的仅是「id / 上级id / 名称」三项，
 * 用来渲染一棵类目树；{@code BookCategory} 上的审计字段（created_by、created_at、
 * updated_by、updated_at、deleted）属于内部运营信息，因此在这里裁掉。
 * 与 {@link ClientBookVo} 同理：本类只做字段投影，不增加任何表以外的字段。</p>
 *
 * <p>返回的是**扁平列表**而不是组装好的树：与后台图书页一致，由前端用若依自带的
 * handleTree 组装（后端返回树会多一次递归组装，且前端想按其它维度分组时又得拍平）。</p>
 *
 * @author ruoyi
 */
public class ClientBookCategoryVo
{
    /** 类目ID */
    private Integer id;

    /** 上级类目ID，顶级为 0 */
    private Integer parentId;

    /** 类目名称 */
    private String title;

    /** 显示顺序，前端按它排序 */
    private Integer orderNum;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getParentId()
    {
        return parentId;
    }

    public void setParentId(Integer parentId)
    {
        this.parentId = parentId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }
}
