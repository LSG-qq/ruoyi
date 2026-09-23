package com.ruoyi.system.enums;

/**
 * 借阅申请状态
 *
 * <p>对应 ssk_borrow_request.status 列。该列的取值口径只在这里定义一处，
 * 业务层判断状态、接口返回状态下拉选项、前端展示中文标签都以本枚举为准，
 * 避免出现「前端写一套、后端写一套、SQL 里再判断一套」的三份口径。
 *
 * <p>状态流转是单向的，只有「待处理」可以被处理，处理之后不再回退：
 * <pre>
 *   待处理 ready ──同意(生成借阅记录)──&gt; 已同意 resolved
 *              └─拒绝(不生成借阅记录)──&gt; 已拒绝 rejected
 * </pre>
 * 因此同意与拒绝都不会把状态改回 ready，重复提交只会得到可读的提示，
 * 真正的并发兜底落在 SQL 的 {@code where status = 'ready'} 条件上。
 *
 * @author ruoyi
 */
public enum BorrowRequestStatus
{
    /** 待处理：囚犯在终端提交申请后、管理员尚未审核 */
    READY("ready", "待处理"),

    /** 已同意：审核通过，同时已生成一条借阅记录并扣减库存 */
    RESOLVED("resolved", "已同意"),

    /** 已拒绝：审核不通过，不产生借阅记录，也不影响书籍库存 */
    REJECTED("rejected", "已拒绝");

    /** 数据库中存放的状态值，长度受 ssk_borrow_request.status varchar(20) 限制 */
    private final String value;

    /** 中文标签，用于接口返回下拉选项与列表展示 */
    private final String label;

    /**
     * 构造枚举项
     *
     * @param value 数据库存储值
     * @param label 中文标签
     */
    BorrowRequestStatus(String value, String label)
    {
        this.value = value;
        this.label = label;
    }

    /**
     * 获取数据库存储值
     *
     * @return 存储值，如 ready
     */
    public String getValue()
    {
        return value;
    }

    /**
     * 获取中文标签
     *
     * @return 中文标签，如 待处理
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * 按存储值匹配枚举
     *
     * <p>匹配不到时返回 null 而不是抛异常：状态值来自数据库，出现无法识别的值属于脏数据，
     * 调用方需要的是「能判断出这是个坏值」，而不是在枚举里打断整个请求。</p>
     *
     * @param value 数据库存储值
     * @return 匹配到的枚举项，匹配不到或入参为空时返回 null
     */
    public static BorrowRequestStatus of(String value)
    {
        if (value == null)
        {
            return null;
        }
        for (BorrowRequestStatus status : values())
        {
            if (status.value.equals(value))
            {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断存储值是否为合法的申请状态
     *
     * <p>列表查询用它把非法状态归一化成「不筛选」，而不是把非法值直接丢给 SQL。</p>
     *
     * @param value 待判断的存储值
     * @return true 合法，false 非法或为空
     */
    public static boolean isValid(String value)
    {
        return of(value) != null;
    }
}
