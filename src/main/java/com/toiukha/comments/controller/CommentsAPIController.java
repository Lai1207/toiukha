package com.toiukha.comments.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.toiukha.comments.model.CommentsDTO;
import com.toiukha.comments.model.CommentsService;
import com.toiukha.comments.model.CommentsVO;
import com.toiukha.forum.article.entity.Article;
import com.toiukha.forum.article.model.ArticleServiceImpl;
import com.toiukha.members.model.MembersService;
import com.toiukha.members.model.MembersVO;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/commentsAPI")
public class CommentsAPIController {

	@Autowired
	CommentsService commentsService;
	@Autowired
	ArticleServiceImpl articleServiceImpl;
	@Autowired
	MembersService membersService;

	
	//========== 前台後台通用 ==========//
	
	//查文章編號
	@GetMapping("/getArt/{artId}")
	public Article getArt(@PathVariable Integer artId) {
		return articleServiceImpl.getArticleById(artId);
	}
	
	//查文章的留言
	@PostMapping("/getComments")
	public List<CommentsDTO> getComments(
			@RequestParam("commArt")Integer commArt) {
		List<CommentsDTO> list = new ArrayList<CommentsDTO>();
		for(CommentsVO cVO : commentsService.getArtComm(commArt)) {
			list.add(CommentsDTO.from(cVO, membersService.getById(cVO.getCommHol()).getMemName()));
		}
		return list;
	}
	
	//查留言
	@PostMapping("/getOneComment")
	public CommentsVO getOneComment(
			@RequestParam("commHol")Integer commHol) {
		return commentsService.getOne(commHol);
	}
	
	@PostMapping("/addComments")
	public CommentsDTO addComments(
	        @RequestParam("commHol") Integer commHol,
	        @RequestParam("commArt") Integer commArt,
	        @RequestParam("commCon") String commCon,
	        @RequestParam("commImg") MultipartFile commImgFile) {

	    try {
	        byte[] commImg = commImgFile.getBytes();
	        
	        Byte commCat = null;
	        if(articleServiceImpl.getArticleById(commArt).getArtCat() >= 2) {
	        	commCat = 2;
	        }else {
	        	commCat = 1;
	        }

	        // 手動建立 CommentsVO 物件
	        CommentsVO commentsVO = new CommentsVO(commCat, commHol, commArt, commCon, commImg);

	        CommentsVO newcommentsVO = commentsService.editOne(commentsVO);
	        newcommentsVO.setCommCreTime(Timestamp.from(Instant.now()));
	        return CommentsDTO.from(newcommentsVO, membersService.getById(newcommentsVO.getCommHol()).getMemName());
	        
	    } catch (IOException e) {
	        e.printStackTrace();
	        return null;
	    }
	}
	
	//修改留言
	@PostMapping("/updateComments")
	public CommentsDTO updateComments(
			@RequestParam(value = "commImg", required = false)MultipartFile part,
			@RequestParam("commCon") String commCon,
			@RequestParam("commId") Integer commId) {
		byte[] commImg = null;
		if (part != null && !part.isEmpty()) {
			try {
				commImg = part.getBytes();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}

		CommentsVO commentsVO = commentsService.changeComm(commId, commCon, commImg);
		
		return CommentsDTO.from(commentsVO, membersService.getById(commentsService.getOne(commId).getCommHol()).getMemName());
	}
	
	//最佳解
	@PostMapping("/bestAnswer")
	public List<CommentsVO> bestAnswer(
			@RequestParam("commId") Integer commId) {
		commentsService.changeSta(commId, (byte)3);
		Article article = articleServiceImpl.getArticleById(commentsService.getOne(commId).getCommArt());
		article.setArtCat((byte)3);
		articleServiceImpl.update(article);
		return commentsService.getArtComm(commentsService.getOne(commId).getCommArt());
	}
	
	//刪除留言
		@PostMapping("/deleteComments")
		public void deleteComments(
				@RequestParam("commId") Integer commId) {
			System.out.println("刪除留言：" + commId);
			commentsService.changeSta(commId, (byte)2);
		}

	//刪除留言
	@PostMapping("/adminDeleteComments")
	public void adminDeleteComments(
			@RequestParam("commId") Integer commId) {
		System.out.println("刪除留言：" + commId);
		commentsService.changeSta(commId, (byte)2);
	}
		
	
	
}
