package com.ruoyi.system.biz.ssk;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.domain.vo.BorrowRequestVo;
import com.ruoyi.system.enums.BorrowRequestStatus;

/**
 * 借阅申请的校验规则与提示文案
 *
 * <p>从 {@link BorrowRequestBiz} 中抽出的**纯函数**集合：只依赖入参，不查数据库、
 * 不取登录态、不写库，可以脱离 Spring 上下文直接单测。</p>
 *
 * <p>抽出它们的原因有两条：</p>
 * <ul>
 *     <li>{@code BorrowRequestBiz} 的流程编排（占用状态、生成借阅记录、扣库存）
 *         与「什么算合法、失败时怎么说」混在一起时，类行数会突破工程规范里的 500 行上限；</li>
 *     <li>这一层是用户**唯一能看见**的部分 —— 校验口径与提示文案集中一处，
 *         才不会出现「同一种失败在两个入口给出两种说法」。</li>
 * </ul>
 *
 * <p>本类只做搬运，**拆分前后行为完全一致**：异常类型、提示文案、边界取值都没有调整。
 * 需要查库的校验（申请是否存在、是否已有待处理申请）仍留在 {@link BorrowRequestBiz}，
 * 涉及登录态的取值（囚号、审核人）也留在那里，不在这里补。</p>
 *
 * @author ruoyi
 */
public final class BorrowRequestRules
{
    /** 借阅天数的默认值：同意申请时未指定则按 7 天计算应还时间 */
    public static final int DEFAULT_BORROW_DAYS = 7;

    /** 借阅天数下限 */
    public static final int MIN_BORROW_DAYS = 1;

    /**
     * 借阅天数上限
     *
     * <p>直接取借阅记录侧 {@link BorrowRecordBiz#RETURN_TIME_MAX_DAYS} 的定义，
     * 不在本类再写一遍数字：同一个上限散在两处，改一处漏一处就会出现
     * 「申请这边能批 400 天、借出接口却拒绝 400 天」这种前后矛盾。</p>
     */
    public static final int MAX_BORROW_DAYS = BorrowRecordBiz.RETURN_TIME_MAX_DAYS;

    /** 时间格式化模板，仅用于拼接提示信息 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 工具类不允许实例化
     *
     * <p>声明出来是为了让「本类只有静态方法」成为编译期约束，而不是靠约定。</p>
     */
    private BorrowRequestRules()
    {
    }

    /**
     * 借阅天数归一化：为空落默认值，越界直接拒绝
     *
     * <p>「默认 7 天」与「{@link #MIN_BORROW_DAYS} ~ {@link #MAX_BORROW_DAYS} 天」都属于
     * 只在特定操作上成立的约束，因此写在业务层而不是实体上；实体上的校验注解会对
     * 所有用到该实体的操作同时生效，那是最容易踩的坑。</p>
     *
     * <p>提交申请与审核同意两条路径共用本方法：提交时校验囚犯选的天数，
     * 同意时校验审核人覆盖的天数（未覆盖则传入申请行的 borrow_days）。
     * 上下限只有这一处定义，不会出现「申请侧允许 400 天、借出侧却拒绝 400 天」这种前后矛盾。</p>
     *
     * @param borrowDays 待校验的借阅天数
     * @return 可用的借阅天数
     */
    public static int normalizeBorrowDays(Integer borrowDays)
    {
        if (borrowDays == null)
        {
            return DEFAULT_BORROW_DAYS;
        }
        if (borrowDays < MIN_BORROW_DAYS || borrowDays > MAX_BORROW_DAYS)
        {
            throw new ServiceException("借阅天数需在 " + MIN_BORROW_DAYS + " ~ " + MAX_BORROW_DAYS + " 天之间");
        }
        return borrowDays;
    }

    /**
     * 查询条件去空格，空串按未填写处理
     *
     * @param value 条件值
     * @return 处理后的条件值，未填写时返回 null
     */
    public static String normalizeQueryText(String value)
    {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 申请状态归一化，非法值按「不筛选」处理
     *
     * <p>合法值直接以枚举为准，因此这里不需要单独维护一份状态清单。</p>
     *
     * @param status 前端传入的状态
     * @return 合法状态，非法或为空时返回 null
     */
    public static String normalizeStatus(String status)
    {
        String trimmed = StringUtils.trim(status);
        return BorrowRequestStatus.isValid(trimmed) ? trimmed : null;
    }

    /**
     * 校验申请当前处于「待处理」状态
     *
     * <p>这是并发兜底之外的第一层拦截，作用是把「已经被处理过」讲清楚，
     * 而不是只丢一句「操作失败」。</p>
     *
     * <p>文案直接嵌入枚举的中文标签，因此不能写成「的申请已 + 标签」——
     * 标签本身就以「已」开头（已同意 / 已拒绝），拼起来会变成「已已同意」。
     * 处理时间只在真的存在时才拼进提示，避免出现「（时间未知）」这种噪声。</p>
     *
     * @param request 申请
     */
    public static void requireReady(BorrowRequestVo request)
    {
        if (BorrowRequestStatus.READY.getValue().equals(request.getStatus()))
        {
            return;
        }
        BorrowRequestStatus status = BorrowRequestStatus.of(request.getStatus());
        String bookName = displayBookName(request.getBookName());
        if (status == null)
        {
            throw new ServiceException("《" + bookName + "》的申请状态异常，无法处理，请联系管理员核对数据");
        }
        String timeText = request.getUpdatedAt() == null
                ? "" : "（处理时间：" + formatDateTime(request.getUpdatedAt()) + "）";
        throw new ServiceException("《" + bookName + "》的申请当前状态为「" + status.getLabel()
                + "」" + timeText + "，无需重复处理");
    }

    /**
     * 取申请行里的囚号，为空时抛出业务异常
     *
     * <p>正常路径下不会为空：终端在提交申请时就写入了囚号，列也是 NOT NULL。
     * 唯一可能为空的是「补这一列之前就存在的老申请」——升级脚本给老行补的是空串。</p>
     *
     * <p>这类申请确定不了借阅人，不能替它选一个、也不能悄悄生成一条没有囚号的借阅记录，
     * 因此明确拒绝并给出可执行的提示，让管理员核对后自行处理。</p>
     *
     * @param request 申请
     * @return 囚号（已去首尾空格）
     */
    public static String requirePrisonerNumber(BorrowRequestVo request)
    {
        String prisonerNumber = StringUtils.trim(request.getPrisonerNumber());
        if (StringUtils.isEmpty(prisonerNumber))
        {
            throw new ServiceException("《" + displayBookName(request.getBookName())
                    + "》的申请没有记录囚号（多为补列之前的历史数据），无法确定借阅人，请核对后再处理");
        }
        return prisonerNumber;
    }

    /**
     * 校验书籍当前有可借库存
     *
     * <p>库存为 0 时不允许提交申请。注意这个校验**不是**防超借：申请本身不扣库存，
     * 库存在「同意」那一步才经库存增量语句扣减，那里才是真正的超借保护。
     * 这里拦的是「注定会被拒的申请」—— 等管理员审核时才发现没库存，对囚犯和管理员都是白跑一趟。</p>
     *
     * @param book 书籍
     */
    public static void requireStockAvailable(BookVo book)
    {
        Integer stock = book.getStockQuantity();
        if (stock == null || stock.intValue() <= 0)
        {
            throw new ServiceException("《" + displayBookName(book.getName()) + "》暂无可借库存，暂时无法申请");
        }
    }

    /**
     * 重复提交申请的提示文案，先查命中与唯一约束拦截两处共用同一句
     *
     * @param bookName 书籍名称
     * @return 提示文案
     */
    public static String duplicateReadyRequestMessage(String bookName)
    {
        return "《" + displayBookName(bookName) + "》你已经提交过申请，正在等待审核，请勿重复提交";
    }

    /**
     * 并发或重复处理的提示文案
     *
     * <p>被 SQL 的乐观锁条件挡住时使用，与先查命中的提示刻意区分开：
     * 先查命中说明是「同一个人点错了」，这里说明是「两个人抢同一条」。</p>
     *
     * @param bookName 书籍名称
     * @return 提示文案
     */
    public static String concurrentMessage(String bookName)
    {
        return "《" + displayBookName(bookName) + "》的申请刚刚已被其他人处理，请刷新列表后查看";
    }

    /**
     * 在指定时间上偏移天数
     *
     * @param base 基准时间
     * @param days 偏移天数
     * @return 偏移后的时间
     */
    public static Date offsetDays(Date base, int days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /**
     * 书籍名称兜底显示
     *
     * <p>书籍被逻辑删除后依然能 join 出名称，真正取不到值时用「（书籍已删除）」代替，
     * 避免提示里出现「《null》」这种文案。</p>
     *
     * @param bookName 书籍名称
     * @return 可展示的书籍名称
     */
    private static String displayBookName(String bookName)
    {
        return StringUtils.isEmpty(StringUtils.trim(bookName)) ? "（书籍已删除）" : bookName;
    }

    /**
     * 把时间格式化为「yyyy-MM-dd HH:mm:ss」，用于拼接提示信息
     *
     * @param time 时间，调用方需保证非空（仅用于拼接已处理申请的处理时间）
     * @return 格式化结果
     */
    private static String formatDateTime(Date time)
    {
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(time);
    }
}
