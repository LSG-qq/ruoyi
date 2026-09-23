package com.ruoyi.system.biz.ssk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.BorrowRequest;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.domain.vo.BorrowRequestVo;
import com.ruoyi.system.enums.BorrowRequestStatus;
import com.ruoyi.system.service.IBorrowRequestService;

/**
 * 借阅申请 业务层
 *
 * <p>承接 controller 与 service 之间的业务编排，包含以下规则：</p>
 * <ul>
 *     <li>列表：书籍名称模糊匹配、状态等值匹配，过滤条件都下沉到 SQL，保证分页总数准确；</li>
 *     <li>我的申请：囚号取自登录令牌并**等值**匹配（不接受前端传入，也不用模糊匹配，
 *         否则会把别人的申请一并查出来）；</li>
 *     <li>提交申请（终端）：囚号取自登录令牌；书籍必须存在且可借库存大于 0；
 *         同一囚号对同一本书不允许存在第二条「待处理」申请（先查 + 唯一索引兜底）；</li>
 *     <li>提交申请：借阅天数由囚犯选定，超出
 *         {@link #MIN_BORROW_DAYS} ~ {@link #MAX_BORROW_DAYS} 天直接拒绝；</li>
 *     <li>同意：在**同一个事务**内先占用申请状态、再生成借阅记录并扣减库存，
 *         任一步失败整体回滚，不会出现「书借出去了但申请还是待处理」或反之；</li>
 *     <li>同意：借阅人（囚号）取申请行的 prisoner_number，**不接受前端传入** ——
 *         申请是谁提的就该是谁借，审核环节只决定「批不批」、不决定「借给谁」；</li>
 *     <li>同意：借阅天数未指定时取**囚犯在终端申请时选定的天数**（申请行的 borrow_days），
 *         审核人可覆盖；两者都取不到时落默认 {@link #DEFAULT_BORROW_DAYS} 天，
 *         超出 {@link #MIN_BORROW_DAYS} ~ {@link #MAX_BORROW_DAYS} 天直接拒绝；</li>
 *     <li>同意/拒绝：只允许处理「待处理」的申请，重复提交或并发提交会得到可读提示而不是脏数据；</li>
 *     <li>审计字段（审核人、审核时间）与申请时间一律由后端生成，不接受前端传入。</li>
 * </ul>
 *
 * @author ruoyi
 */
@Component
public class BorrowRequestBiz
{
    /** 借阅天数的默认值：同意申请时未指定则按 7 天计算应还时间 */
    private static final int DEFAULT_BORROW_DAYS = 7;

    /** 借阅天数下限 */
    private static final int MIN_BORROW_DAYS = 1;

    /**
     * 借阅天数上限
     *
     * <p>直接取借阅记录侧 {@link BorrowRecordBiz#RETURN_TIME_MAX_DAYS} 的定义，
     * 不在本类再写一遍数字：同一个上限散在两处，改一处漏一处就会出现
     * 「申请这边能批 400 天、借出接口却拒绝 400 天」这种前后矛盾。</p>
     */
    private static final int MAX_BORROW_DAYS = BorrowRecordBiz.RETURN_TIME_MAX_DAYS;

    /** 时间格式化模板，仅用于拼接提示信息 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private IBorrowRequestService borrowRequestService;

    /**
     * 复用借阅记录的业务层，而不是直接操作借阅记录表
     *
     * <p>「同意申请」产生的借阅记录必须走与手工借出完全相同的入口，
     * 才能同时拿到库存扣减（不得扣成负数）与「同囚号同书只能有一条未归还记录」
     * 的两层拦截（业务层先查 + 数据库唯一索引兜底）。
     * 在这里重新拼一套插入逻辑必然会漏掉其中某一项。</p>
     */
    @Autowired
    private BorrowRecordBiz borrowRecordBiz;

    /**
     * 复用图书业务层，用于提交申请前取书籍信息与可借库存
     *
     * <p>不在这边另写一条查图书的语句：库存的写入方只有图书侧的
     * {@code BookService#updateStockById}，「可借库存」的口径只有一处，
     * 在申请侧重写一份查询容易在口径上分叉。</p>
     */
    @Autowired
    private BookBiz bookBiz;

    /**
     * 按条件分页查询借阅申请
     *
     * @param bookName 书籍名称，为空表示不带条件
     * @param status   申请状态，为空或非法值表示不带条件
     * @return 借阅申请集合
     */
    public List<BorrowRequestVo> findPage(String bookName, String status)
    {
        return borrowRequestService.findPage(normalizeQueryText(bookName), normalizeStatus(status));
    }

    /**
     * 查询本人的借阅申请（终端的「我的申请」）
     *
     * <p>囚号取自登录令牌，因此本方法不收囚号参数：一旦接受囚号入参，
     * 终端只要改一个参数就能翻看别人的申请。这与「审核不决定借给谁」是同一条原则
     * ——凡是能决定「是谁的数据」的入参，都不接受前端传入。</p>
     *
     * @param status 申请状态，为空或非法值表示不筛选
     * @return 本人的借阅申请集合
     */
    public List<BorrowRequestVo> findMyPage(String status)
    {
        return borrowRequestService.findPageByPrisoner(currentPrisonerNumber(), normalizeStatus(status));
    }

    /**
     * 查询申请状态的下拉选项
     *
     * <p>选项由 {@link BorrowRequestStatus} 枚举直接派生，前端不再自己抄一份状态字典：
     * 枚举改了取值或标签，页面下拉会跟着变，不会出现两边口径不一致。
     * 返回结构固定为 {@code [{value, label}]}，「全部」这一项属于展示层概念（值为空），
     * 由前端自行补上，不放进枚举。</p>
     *
     * @return 状态下拉选项
     */
    public List<Map<String, String>> findStatusOptions()
    {
        List<Map<String, String>> options = new ArrayList<>();
        for (BorrowRequestStatus status : BorrowRequestStatus.values())
        {
            Map<String, String> option = new LinkedHashMap<>();
            option.put("value", status.getValue());
            option.put("label", status.getLabel());
            options.add(option);
        }
        return options;
    }

    /**
     * 借阅天数的默认值与上下限
     *
     * <p>同意弹窗里的输入框要用到这三项：默认值、最小值 {@code :min}、最大值 {@code :max}，
     * 以及「天，默认 N 天」这句提示文案。前端自己写一份数字就必须靠人工与后端对齐，
     * 只改后端不改前端时会出现「界面允许填 400 天、提交后被后端拒绝」这种自相矛盾；
     * 因此统一从这里下发，{@link #DEFAULT_BORROW_DAYS} / {@link #MIN_BORROW_DAYS} /
     * {@link #MAX_BORROW_DAYS} 是唯一权威来源，前端不再写死数字。</p>
     *
     * <p>返回结构固定为 {@code {defaultDays, minDays, maxDays}}，
     * 与 {@link #findStatusOptions()} 一样属于页面渲染所需的元数据。</p>
     *
     * @return 借阅天数的默认值与上下限
     */
    public Map<String, Integer> findBorrowDayLimits()
    {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("defaultDays", DEFAULT_BORROW_DAYS);
        limits.put("minDays", MIN_BORROW_DAYS);
        limits.put("maxDays", MAX_BORROW_DAYS);
        return limits;
    }

    /**
     * 提交借阅申请（终端）
     *
     * <p>囚号取自登录令牌（囚号即登录名），申请时间取当前系统时间，状态固定为「待处理」，
     * 这三项都不接受前端传入 —— 否则终端只要改一个参数就能替别人提交申请，
     * 或者把申请伪装成「已同意」绕过审核。</p>
     *
     * <p>校验顺序刻意是「天数 → 书籍与库存 → 重复提交」：三步都在写库之前，
     * 非法请求不会白占一次自增值，也不会让唯一索引先于可读提示被触发
     * （数据库抛出的冲突异常对使用者没有意义）。</p>
     *
     * @param bookId     书籍ID
     * @param borrowDays 囚犯选定的借阅天数，为空时按默认 {@link #DEFAULT_BORROW_DAYS} 天
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(Integer bookId, Integer borrowDays)
    {
        String prisonerNumber = currentPrisonerNumber();
        int days = normalizeBorrowDays(borrowDays);
        BookVo book = bookBiz.findById(bookId);
        requireStockAvailable(book);
        requireNoReadyRequest(book.getId(), prisonerNumber, book.getName());

        BorrowRequest request = new BorrowRequest();
        request.setBookId(book.getId());
        request.setPrisonerNumber(prisonerNumber);
        request.setBorrowDays(days);
        request.setStatus(BorrowRequestStatus.READY.getValue());
        request.setCreatedAt(new Date());
        // 审核字段与逻辑删除标记一律由后端生成，申请刚提交时它们必然为空
        request.setUpdatedBy(null);
        request.setUpdatedAt(null);
        request.setDeleted(null);
        try
        {
            return borrowRequestService.create(request);
        }
        catch (DuplicateKeyException e)
        {
            // 唯一索引 uk_ssk_borrow_request_active 的兜底：终端上囚犯连点提交时，
            // 两个并发请求可能都通过了上面的先查校验，后写入的那条会被数据库拒绝。
            // 这里转成与先查命中一致的提示，避免把数据库异常直接抛给使用者。
            throw new ServiceException(duplicateReadyRequestMessage(book.getName()));
        }
    }

    /**
     * 同意申请：生成借阅记录并扣减库存，同时把申请置为已同意
     *
     * <p>步骤顺序是先占用状态、再借出，原因有两点：</p>
     * <ol>
     *     <li>状态更新语句自带 {@code where status = 'ready'} 条件，相当于一把乐观锁，
     *         两个管理员同时点「同意」时只有一个能拿到影响行数 1，另一个直接失败退出，
     *         不会走到扣库存那一步；</li>
     *     <li>先借出再改状态的话，失败回滚虽然也能保证数据一致，但白耗一次
     *         借阅记录的自增值，也会让「重复借阅」的唯一索引先于状态校验被触发，
     *         错误提示会变得难以理解。</li>
     * </ol>
     *
     * <p>借阅人直接取申请行里的囚号 —— 申请由终端在提交那一刻写入，记的就是当时登录的囚号。
     * 本方法因此不收囚号参数：申请是谁提的就该是谁借，允许在审核环节手输会出现
     * 「申请人与借阅人对不上」且事后无从追查。</p>
     *
     * @param id         申请ID
     * @param borrowDays 借阅天数，为空时按默认 {@link #DEFAULT_BORROW_DAYS} 天
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int approve(Integer id, Integer borrowDays)
    {
        BorrowRequestVo request = requireRequest(id);
        requireReady(request);
        // 囚号校验也在占用状态之前：历史数据（补列之前的老申请）囚号为空，
        // 这类申请确定不了借阅人，要在改状态之前就拦住，别白占一次状态再回滚
        String prisonerNumber = requirePrisonerNumber(request);
        // 先把天数算清楚：非法入参不该等到占用完状态再失败。
        // 审核人没指定天数时取囚犯在终端申请时选的 borrow_days；
        // 申请行的值也为空（补列之前的老数据）则由 normalizeBorrowDays 落默认值。
        int days = normalizeBorrowDays(borrowDays != null ? borrowDays : request.getBorrowDays());

        Integer operator = currentUserId();
        Date now = new Date();
        if (borrowRequestService.updateStatusById(id, BorrowRequestStatus.RESOLVED.getValue(),
                BorrowRequestStatus.READY.getValue(), operator, now) == 0)
        {
            // 走到这里说明状态已被别人改掉（或申请刚被逻辑删除），属于并发场景
            throw new ServiceException(concurrentMessage(request.getBookName()));
        }

        // 借出时间取当前时间，应还时间 = 借出时间 + 借阅天数。
        // 时间由后端算好后传给借阅记录业务层，因此不会触发它自己的 30 天默认值。
        BorrowRecord record = new BorrowRecord();
        record.setBookId(request.getBookId());
        record.setPrisonerNumber(prisonerNumber);
        record.setBorrowTime(now);
        record.setReturnTime(offsetDays(now, days));
        // 库存不足、囚号为空、同囚号同书已有未归还记录都会在这里抛出业务异常，
        // 事务整体回滚，申请回到待处理，管理员可以修正后重试。
        borrowRecordBiz.create(record);
        return 1;
    }

    /**
     * 拒绝申请：把申请置为已拒绝
     *
     * <p>拒绝不触碰书籍库存，也不生成借阅记录，因此只有一次状态更新；
     * 同样用 {@code where status = 'ready'} 兜底并发与重复提交。</p>
     *
     * @param id 申请ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int reject(Integer id)
    {
        BorrowRequestVo request = requireRequest(id);
        requireReady(request);

        Integer operator = currentUserId();
        Date now = new Date();
        if (borrowRequestService.updateStatusById(id, BorrowRequestStatus.REJECTED.getValue(),
                BorrowRequestStatus.READY.getValue(), operator, now) == 0)
        {
            throw new ServiceException(concurrentMessage(request.getBookName()));
        }
        return 1;
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
    private void requireReady(BorrowRequestVo request)
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
     * 查询申请，不存在时抛出业务异常
     *
     * @param id 申请ID
     * @return 申请（含书籍信息）
     */
    private BorrowRequestVo requireRequest(Integer id)
    {
        if (id == null)
        {
            throw new ServiceException("借阅申请ID不能为空");
        }
        BorrowRequestVo request = borrowRequestService.findById(id);
        if (request == null)
        {
            throw new ServiceException("借阅申请不存在或已被删除，请刷新后重试");
        }
        return request;
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
    private String requirePrisonerNumber(BorrowRequestVo request)
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
    private void requireStockAvailable(BookVo book)
    {
        Integer stock = book.getStockQuantity();
        if (stock == null || stock.intValue() <= 0)
        {
            throw new ServiceException("《" + displayBookName(book.getName()) + "》暂无可借库存，暂时无法申请");
        }
    }

    /**
     * 校验该囚号对这本书没有仍在等待审核的申请
     *
     * <p>这是「同一囚号同书只能有一条待处理申请」的第一层拦截，只用于给出可读提示；
     * 并发兜底是唯一索引 uk_ssk_borrow_request_active（生成列 active_request_key）。</p>
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @param bookName       书籍名称，仅用于提示文案
     */
    private void requireNoReadyRequest(Integer bookId, String prisonerNumber, String bookName)
    {
        if (borrowRequestService.countReadyByBookAndPrisoner(bookId, prisonerNumber) > 0)
        {
            throw new ServiceException(duplicateReadyRequestMessage(bookName));
        }
    }

    /**
     * 重复提交申请的提示文案，先查命中与唯一约束拦截两处共用同一句
     *
     * @param bookName 书籍名称
     * @return 提示文案
     */
    private String duplicateReadyRequestMessage(String bookName)
    {
        return "《" + displayBookName(bookName) + "》你已经提交过申请，正在等待审核，请勿重复提交";
    }

    /**
     * 获取当前登录账号的用户名，即囚号
     *
     * <p>「囚号即登录名」是本模块的既定约定，因此这里不需要再查映射表，
     * 也不会出现「登录名与囚号对不上」的情况。返回前统一去首尾空格，
     * 避免因为一个尾随空格导致同一囚号在库里出现两种写法、等值查询查不全。</p>
     *
     * @return 囚号
     */
    private String currentPrisonerNumber()
    {
        String username = StringUtils.trim(SecurityUtils.getUsername());
        if (StringUtils.isEmpty(username))
        {
            throw new ServiceException("获取当前登录用户信息失败");
        }
        return username;
    }

    /**
     * 借阅天数归一化：为空落默认值，越界直接拒绝
     *
     * <p>「默认 7 天」与「1 ~ {@link #MAX_BORROW_DAYS} 天」都属于只在特定操作上成立的约束，
     * 因此写在业务层而不是实体上；实体上的校验注解会对所有用到该实体的操作同时生效，
     * 是本次最容易踩的坑。</p>
     *
     * <p>提交申请与审核同意两条路径共用本方法：提交时校验囚犯选的天数，
     * 同意时校验审核人覆盖的天数（未覆盖则传入申请行的 borrow_days）。
     * 上下限只有这一处定义，不会出现「申请侧允许 400 天、借出侧却拒绝 400 天」这种前后矛盾。</p>
     *
     * @param borrowDays 待校验的借阅天数
     * @return 可用的借阅天数
     */
    private int normalizeBorrowDays(Integer borrowDays)
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
     * 获取当前登录用户ID，用于填充审核人
     *
     * @return 当前登录用户ID
     */
    private Integer currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("获取当前登录用户信息失败");
        }
        return userId.intValue();
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
    private String concurrentMessage(String bookName)
    {
        return "《" + displayBookName(bookName) + "》的申请刚刚已被其他人处理，请刷新列表后查看";
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
    private String displayBookName(String bookName)
    {
        return StringUtils.isEmpty(StringUtils.trim(bookName)) ? "（书籍已删除）" : bookName;
    }

    /**
     * 查询条件去空格，空串按未填写处理
     *
     * @param value 条件值
     * @return 处理后的条件值，未填写时返回 null
     */
    private String normalizeQueryText(String value)
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
    private String normalizeStatus(String status)
    {
        String trimmed = StringUtils.trim(status);
        return BorrowRequestStatus.isValid(trimmed) ? trimmed : null;
    }

    /**
     * 在指定时间上偏移天数
     *
     * @param base 基准时间
     * @param days 偏移天数
     * @return 偏移后的时间
     */
    private Date offsetDays(Date base, int days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /**
     * 把时间格式化为「yyyy-MM-dd HH:mm:ss」，用于拼接提示信息
     *
     * @param time 时间，调用方需保证非空（仅用于拼接已处理申请的处理时间）
     * @return 格式化结果
     */
    private String formatDateTime(Date time)
    {
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(time);
    }
}
