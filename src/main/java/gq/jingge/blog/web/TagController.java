package gq.jingge.blog.web;

import gq.jingge.blog.domain.Post;
import gq.jingge.blog.domain.Tag;
import gq.jingge.blog.error.NotFoundException;
import gq.jingge.blog.service.AppSetting;
import gq.jingge.blog.service.PostService;
import gq.jingge.blog.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.springframework.web.bind.annotation.RequestMethod.*;
/**
 * Created by wangyunjing on 2017/12/10.
 */
@Controller
@RequestMapping("tags")
public class TagController {

    @Autowired
    private TagService tagService;

    @Autowired
    private PostService postService;

    @Autowired
    private AppSetting appSetting;

    @RequestMapping(value = "", method = GET)
    public String index(Model model){
        model.addAttribute("tags", postService.countPostsByTags());
        return "tags/index";
    }

    @RequestMapping(value = "{tagName}", method = GET)
    public String showTag(@PathVariable String tagName, @RequestParam(defaultValue = "1") int page, Model model) {
        Tag tag = tagService.getTag(tagName);

        if (tag == null) {
            throw new NotFoundException("Tag " + tagName + " is not found.");
        }

        page = page < 1 ? 0 : page - 1;
        Page<Post> posts = postService.findPostsByTag(tagName, page, appSetting.getPageSize());

        model.addAttribute("tag", tag);
        model.addAttribute("posts", posts);
        model.addAttribute("page", page + 1);
        model.addAttribute("totalPages", posts.getTotalPages());

        return "tags/show";
    }
}
