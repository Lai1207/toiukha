package com.toiukha.forum.article.controller;

import com.toiukha.forum.article.dto.ArticleDTO;
//import com.toiukha.forum.article.model.ArticlePicturesServiceImpl;
import com.toiukha.forum.article.model.ArticlePicturesService;
import com.toiukha.forum.article.model.ArticleService;
//import org.hibernate.query.SortDirection;
import com.toiukha.forum.article.model.ArticleServiceImpl;
import com.toiukha.forum.article.model.ArticleStatus;
import com.toiukha.forum.util.Debug;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.*;

import com.toiukha.like.model.LikeService;
import com.toiukha.like.model.LikeVO;
import com.toiukha.forum.article.entity.Article;
import java.sql.Timestamp;
import com.toiukha.articlecollection.model.ArticleCollectionService;
import com.toiukha.articlecollection.model.ArticleCollectionVO;
import com.toiukha.articlecollection.model.ArticleCollectionCompositePrimaryKey;

// 文章相關的 RESTful API Controller
@RestController
public class ArticleController {

    private final ArticleService articleService;
    private final ArticlePicturesService apService;

    private LikeService likeService;
    private ArticleCollectionService articleCollectionService;

    // 使用建構子注入 ArticleService
    @Autowired
    public ArticleController(ArticleService articleService
                            , LikeService likeService
                            , ArticleCollectionService articleCollectionService
                            , ArticlePicturesService apService) {

        this.articleService = articleService;
        this.likeService = likeService;
        this.articleCollectionService = articleCollectionService;
        this.apService = apService;
    }

// 取得單篇文章 DTO，文章上下架都會撈到
    @GetMapping("/article/{artId:\\d+}")
    public ArticleDTO getArticleDTO(@PathVariable Integer artId) {
        Debug.log();
        return articleService.getDTOById(artId);
    }

    // 取得單篇文章 DTO，而且只允許訪問已上架的文章
    @GetMapping("/api/article/{artId}")
    public ResponseEntity<ArticleDTO> getArticleDTOWithPublished(@PathVariable Integer artId) {
        Debug.log(artId);
        ArticleDTO dto = articleService.getDTOById(artId);
        if (dto == null) {
            Debug.log("文章不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }else if (dto.getArtSta() != ArticleStatus.PUBLISHED.getValue()) {
            Debug.log("文章已下架");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); //回傳 403 Forbidden禁止訪問
        }
        return ResponseEntity.ok(dto);
    }

// 取得全部文章 DTO(自訂排序)
    @GetMapping("/articles")
    public List<ArticleDTO> getAllArticles(
            @RequestParam(defaultValue = "ARTICLE_ID") String sortBy,
            @RequestParam(name = "order", defaultValue = "ASC") String sortDirection) {
        // 直接返回 ArticleDTO 列表
        return articleService.getAllDTO(sortBy, sortDirection);
    }


// 取得全部文章 DTO(支援分頁和自訂排序)
    @GetMapping("/articles/paged")
    public ResponseEntity<Map<String, Object>> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "artCreTime") String sortBy,
            @RequestParam(name = "order",defaultValue = "DESC") ArticleServiceImpl.SortDirection dir
    ) {
        // 只查詢上架的文章
        Page<ArticleDTO> articlePage = articleService.getAllPagedDTO(page, size, sortBy, dir, ArticleStatus.PUBLISHED.getValue());

        Map<String, Object> response = new HashMap<>();
        response.put("articles", articlePage.getContent());
        response.put("totalPages", articlePage.getTotalPages());
        response.put("currentPage", articlePage.getNumber());
        response.put("hasNext", articlePage.hasNext());

        return ResponseEntity.ok(response);
    }


    // 查詢某會員是否對某篇文章按過讚
    @PostMapping("/article/isLiked")
    public Integer isLiked(
            @RequestParam("docId") Integer docId,
            @RequestParam("memId") Integer memId) {
        LikeVO like = likeService.getOneLike(docId, memId);
        if (like != null && like.getMemId().equals(memId) && like.getLikeSta() != null) {
            return like.getLikeSta().intValue(); // 1:已按讚, 0:未按讚
        }
        return 0; // 預設未按讚
    }

    // 處理文章按讚功能，同時更新文章的 artLike 欄位
    @PostMapping("/article/like")
    public ResponseEntity<Map<String, Object>> likeArticle(
            @RequestParam("docId") Integer docId,
            @RequestParam("memId") Integer memId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 先取得文章原本的讚數
            Article article = articleService.getArticleById(docId);
            if (article == null) {
                response.put("success", false);
                response.put("message", "找不到文章");
                return ResponseEntity.ok(response);
            }
            
            Integer originalLikeCount = article.getArtLike() != null ? article.getArtLike() : 0;
            
            // 檢查用戶是否已經按過讚
            LikeVO existingLike = likeService.getOneLike(docId, memId);
            boolean wasLiked = false;
            
            if (existingLike != null && existingLike.getMemId().equals(memId)) {
                wasLiked = existingLike.getLikeSta() != null && existingLike.getLikeSta().equals(Byte.valueOf((byte) 1));
            }
            
            // 處理按讚邏輯
            LikeVO likeVO = new LikeVO();
            likeVO.setDocId(docId);
            likeVO.setParDocId(null); // 文章按讚時 parDocId 為 null
            likeVO.setMemId(memId);
            likeVO.setDocType(Byte.valueOf((byte) 0)); // 0 表示文章
            
            Integer Id = null;
            Byte sta = null;
            
            if (existingLike != null) {
                Id = existingLike.getLikeId();
                sta = existingLike.getLikeSta();
            }
            
            // 如果找到了資料且狀態為0，則更新為1；否則更新為0或新增
            if (Id != null && sta != null && sta.equals(Byte.valueOf((byte) 0))) {
                likeVO.setLikeSta(Byte.valueOf((byte) 1));
                likeVO.setLikeId(Id);
                likeVO.setLikeTime(getNowTime());
                likeService.eidtOne(likeVO);
            } else if (Id != null && sta != null && sta.equals(Byte.valueOf((byte) 1))) {
                // 如果Id不為空且狀態為1，表示要取消讚
                likeVO.setLikeSta(Byte.valueOf((byte) 0));
                likeVO.setLikeId(Id);
                likeVO.setLikeTime(getNowTime());
                likeService.eidtOne(likeVO);
            } else {
                // 第一次點讚
                likeVO.setLikeSta(Byte.valueOf((byte) 1));
                likeVO.setLikeTime(getNowTime());
                likeService.eidtOne(likeVO);
            }
            
            // 計算新的總讚數
            Integer newTotalLikeCount;
            if (wasLiked) {
                // 原本已按讚，現在收回讚，所以要減1
                newTotalLikeCount = originalLikeCount - 1;
            } else {
                // 原本未按讚，現在按讚，所以要加1
                newTotalLikeCount = originalLikeCount + 1;
                response.put("message", "按讚成功");
            }
            
            // 確保讚數不會變成負數
            if (newTotalLikeCount < 0) {
                newTotalLikeCount = 0;
            }
            
            // 更新文章的 artLike 欄位
            article.setArtLike(newTotalLikeCount);
            articleService.update(article);
            
            response.put("success", true);
            response.put("likeCount", newTotalLikeCount);
            response.put("wasLiked", wasLiked);
            response.put("message", wasLiked ? "收回讚成功" : "按讚成功");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "按讚失敗: " + e.getMessage());
            System.err.println("文章按讚時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    // 查詢某會員是否收藏了某篇文章
    @PostMapping("/article/isCollected")
    public ResponseEntity<Map<String, Object>> isCollected(
            @RequestParam("artId") Integer artId,
            @RequestParam("memId") Integer memId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            ArticleCollectionCompositePrimaryKey key = new ArticleCollectionCompositePrimaryKey(memId, artId);
            ArticleCollectionVO collection = articleCollectionService.getOne(key);
            
            boolean isCollected = collection != null;
            
            response.put("success", true);
            response.put("isCollected", isCollected);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "檢查收藏狀態失敗: " + e.getMessage());
            System.err.println("檢查收藏狀態時發生錯誤: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // 處理文章收藏/取消收藏功能
    @PostMapping("/article/collect")
    public ResponseEntity<Map<String, Object>> collectArticle(
            @RequestParam("artId") Integer artId,
            @RequestParam("memId") Integer memId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            ArticleCollectionCompositePrimaryKey key = new ArticleCollectionCompositePrimaryKey(memId, artId);
            ArticleCollectionVO existingCollection = articleCollectionService.getOne(key);
            
            if (existingCollection != null) {
                // 如果已經收藏，則取消收藏
                articleCollectionService.deleteOne(key);
                response.put("success", true);
                response.put("isCollected", false);
                response.put("message", "取消收藏成功");
            } else {
                // 如果未收藏，則新增收藏
                ArticleCollectionVO newCollection = new ArticleCollectionVO(key);
                articleCollectionService.addOne(newCollection);
                response.put("success", true);
                response.put("isCollected", true);
                response.put("message", "收藏成功");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "收藏操作失敗: " + e.getMessage());
            System.err.println("文章收藏時發生錯誤: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    // 刪除文章（將 ARTSTA 設為 2）
    @DeleteMapping("/article/{artId}/delete")
    public ResponseEntity<Map<String, Object>> deleteArticle(@PathVariable Integer artId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Article article = articleService.getArticleById(artId);
            if (article == null) {
                response.put("success", false);
                response.put("message", "找不到文章");
                return ResponseEntity.ok(response);
            }
            
            // 將文章狀態設為 2（已刪除）
            article.setArtSta((byte) 2);
            articleService.update(article);
            
            response.put("success", true);
            response.put("message", "文章已刪除");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "刪除文章時發生錯誤: " + e.getMessage());
            System.err.println("刪除文章時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    // 更新文章
    @PutMapping("/article/{artId}/update")
    public ResponseEntity<Map<String, Object>> updateArticle(
            @PathVariable Integer artId,
            @RequestBody Map<String, Object> articleData) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Article article = articleService.getArticleById(artId);
            if (article == null) {
                response.put("success", false);
                response.put("message", "找不到文章");
                return ResponseEntity.ok(response);
            }

            // 儲存更新
            articleService.updateBasic(artId,
                    ((Number) articleData.get("artCat")).byteValue(),
                    ((Number) articleData.get("artSta")).byteValue(),
                    (String) articleData.get("artTitle"),
                    (String) articleData.get("artCon"));

            response.put("success", true);
            response.put("message", "文章更新成功");
            return ResponseEntity.ok(response);
            
        } catch (NoSuchElementException ne){
            response.put("success", false);
            response.put("message", "找不到文章: " + ne.getMessage());
            System.err.println("找不到文章: " + ne.getMessage());
        }
        catch (Exception e) {
            response.put("success", false);
            response.put("message", "更新文章時發生錯誤: " + e.getMessage());
            System.err.println("更新文章時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    Timestamp getNowTime() {
        Date date = new Date();
        Timestamp now = new Timestamp(date.getTime());
        return now;
    }

}