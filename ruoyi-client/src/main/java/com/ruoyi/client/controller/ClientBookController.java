package com.ruoyi.client.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.biz.ssk.BookBiz;
import com.ruoyi.system.biz.ssk.BookCategoryBiz;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.domain.vo.ClientBookCategoryVo;
import com.ruoyi.system.domain.vo.ClientBookVo;

/**
 * 终端 - 图书浏览
 *
 * <p>囚犯在终端上只能「看」图书，不能新增、修改、删除，因此这里只有查询接口。
 * 接口一律不挂 {@code @PreAuthorize}：囚犯角色没有任何权限位，
 * 挂上去等于把所有终端用户挡在门外。访问控制由「已登录」这一层保证
 * （SecurityConfig 的 anyRequest().authenticated()），业务数据的隔离靠囚号。</p>
 *
 * <p>返回体用 {@link ClientBookVo} 而不是后台的 BookVo，把审计字段挡在响应之外，
 * 详见该类注释。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/client/book")
public class ClientBookController extends BaseController
{
    @Autowired
    private BookBiz bookBiz;

    @Autowired
    private BookCategoryBiz bookCategoryBiz;

    /**
     * 分页查询图书列表（浏览）
     *
     * <p>查询条件与管理后台一致（书名、作者、书架号、类目），分页参数由 startPage() 从请求里取。
     * 类目筛选传入的是「选中类目 + 其全部下级类目」，展开在业务层完成。</p>
     *
     * @param book       查询条件（name / author / shelfCode），可为空
     * @param categoryId 选中的类目ID，为空或 0 表示「全部」
     * @return 分页结果
     */
    @GetMapping("/list")
    public TableDataInfo list(Book book, @RequestParam(value = "categoryId", required = false) Integer categoryId)
    {
        startPage();
        return getDataTable(bookBiz.findClientPage(book, categoryId));
    }

    /**
     * 查询图书类目列表（扁平结构，前端自行组装成树）
     *
     * <p>终端要按类目筛选图书，就得先有类目清单。返回扁平列表而不是已组装的树，
     * 与后台图书页的做法保持一致：前端用若依自带的 handleTree 组装。</p>
     *
     * @return 类目列表
     */
    @GetMapping("/categoryList")
    public AjaxResult categoryList()
    {
        List<ClientBookCategoryVo> result = new ArrayList<>();
        for (BookCategory source : bookCategoryBiz.findList())
        {
            result.add(toClientCategory(source));
        }
        return success(result);
    }

    /**
     * 把类目裁剪为终端可见字段
     *
     * @param source 含审计字段的类目
     * @return 仅含终端可见字段的类目
     */
    private ClientBookCategoryVo toClientCategory(BookCategory source)
    {
        ClientBookCategoryVo target = new ClientBookCategoryVo();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setTitle(source.getTitle());
        target.setOrderNum(source.getOrderNum());
        return target;
    }
}
