package com.toiukha.comments.model;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toiukha.like.model.LikeService;

import jakarta.persistence.EntityManager;

@Service
public class CommentsService {

	@Autowired
	private CommentsRepository commentsRepository;
	@Autowired
    private EntityManager entityManager;
	@Autowired
	private LikeService likeService;
	
	//新增、修改、刪除
	@Transactional
	public CommentsVO editOne(CommentsVO commentsVO) {
		CommentsVO inseredCommentsVO = commentsRepository.save(commentsVO);
		entityManager.flush(); // 將所有掛起的變更寫入資料庫
        entityManager.clear(); // 清除一級快取，強制如果下次查詢從資料庫載入
        return inseredCommentsVO;
	}
	
	//查單一留言
	public CommentsVO getOne(Integer commId) {
		Optional<CommentsVO> optional = commentsRepository.findById(commId);
		return optional.orElse(null);
	}
	
	//查文章所屬留言
	public List<CommentsVO> getArtComm(Integer commArt){
		List<CommentsVO> list = commentsRepository.getbyArt(commArt);
		for(CommentsVO cVO : list) {
			Integer likeNum = likeService.getLikeNum(cVO.getCommArt(), cVO.getCommId());
			cVO.setCommLike(likeNum);
		}
		return list;
	}
	
	//改狀態
	public void changeSta(Integer commId, Byte commSta) {
		commentsRepository.upadteSta(commId, commSta);
	}
	
	//改留言內容
	public CommentsVO changeComm(Integer commId, String commCon, byte[] commImg) {
		commentsRepository.upadteComm(commId, commCon, commImg);
		return getNewComm(commId);
	}
	
	public CommentsVO changeComm(Integer commId, String commCon) {
		commentsRepository.upadteComm(commId, commCon);
		return getNewComm(commId);
	}
	
	CommentsVO getNewComm(Integer commId) {
		CommentsVO commentsVO = getOne(commId);
		Integer likeNum = likeService.getLikeNum(commentsVO.getCommArt(), commentsVO.getCommId());
		commentsVO.setCommLike(likeNum);
		return commentsVO;
	}
	
}
