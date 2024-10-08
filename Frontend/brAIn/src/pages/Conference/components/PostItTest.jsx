import React, { useState, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { addItem } from '../../../actions/votedItemAction';
import './PostItTest.css';
import Modal from './Modal';

const PostItTest = ({postItBig}) => {
  const roundRobinBoard = useSelector((state) => state.roundRobinBoard?.roundRobinBoard || []);
  const [currentPage, setCurrentPage] = useState(0);
  const itemsPerPage = 1; // 한 페이지에 보여줄 라운드 수
  const dispatch = useDispatch();

  // 현재 라운드로 자동 이동
  useEffect(() => {
    if (roundRobinBoard.length > 0) {
      setCurrentPage(roundRobinBoard.length - 1); // 마지막 라운드로 이동
    }
  }, [roundRobinBoard]);

  // 페이지 버튼 클릭 핸들러
  const handlePageChange = (pageNumber) => {
    setCurrentPage(pageNumber);
  };

  // 페이지네이션 관련 데이터 계산
  const totalPages = Math.ceil(roundRobinBoard.length / itemsPerPage);
  const currentRoundIdeas = roundRobinBoard.slice(currentPage * itemsPerPage, (currentPage + 1) * itemsPerPage);

  // 파스텔 톤 색상 배열
  const colors = [
    '#F8CFCF', // 분홍색
    '#FFFFC2', // 노란색
    '#C9E4C5', // 녹색
    '#D6C0EB', // 보라색
    '#E0FFFF', // 라이트 시안색
    '#F7B7A3', // 연한 오렌지색
    '#D0E6F8'  // 파스텔 블루
  ];

  // 인덱스를 기반으로 색상 선택 함수
  const getColorForIdea = (ideaIndex) => {
    return colors[ideaIndex % colors.length];
  };

  // 투표 핸들러
  const handleVote = (round, index, content) => {
    console.log('Dispatching vote with:', { round, index, content });
    dispatch(addItem(round, index, content));
  };

  return (
    <div className="post-it-board">
      {/* 페이지네이션 버튼 */}
      <div className="pagination">
        {Array.from({ length: totalPages }).map((_, index) => (
          <button
            key={index}
            onClick={() => handlePageChange(index)}
            className={index === currentPage ? 'active' : 'non'}
          >
            <h3>Rnd.{index + 1}</h3>
          </button>
        ))}
      </div>
      {currentRoundIdeas.length === 0 ? (
        <p>보드에 아이디어가 없습니다.</p>
      ) : (
        currentRoundIdeas.map((roundIdeas, roundIndex) => (
          <div key={roundIndex} className="round-container">
            <div className="post-it-container">
              {roundIdeas && roundIdeas.length > 0 ? (
                roundIdeas.map((idea, ideaIndex) => (
                  <div
                    key={ideaIndex}
                    className="post-it-card"
                    style={{ backgroundColor: getColorForIdea(ideaIndex) }} // 아이디어별 순차 색상 적용
                  >
                    <div className='post-it-content' onClick={() => postItBig(idea)}>{idea}</div>
                    <button onClick={() => handleVote(currentPage + 1, ideaIndex, idea)}>Vote</button>
                  </div>
                ))
              ) : (
                <p>아이디어가 없습니다.</p>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
};

export default PostItTest;
