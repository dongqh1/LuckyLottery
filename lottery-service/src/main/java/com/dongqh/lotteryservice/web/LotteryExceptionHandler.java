package com.dongqh.lotteryservice.web;

import com.dongqh.lotteryservice.domain.LotteryDomainException;
import com.dongqh.lotteryservice.activityprize.domain.ActivityPrizeException;
import com.dongqh.lotteryservice.prizecatalog.domain.PrizeCatalogException;
import com.dongqh.lotteryservice.prizecatalog.domain.RewardDefinitionException;
import com.hmall.common.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dongqh.lotteryservice")
public class LotteryExceptionHandler {
    @ExceptionHandler(LotteryDomainException.class)
    public ResponseEntity<R<Void>> handleLotteryError(LotteryDomainException exception) {
        var code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(R.error(code.code(), code.message()));
    }

    @ExceptionHandler(ActivityPrizeException.class)
    public ResponseEntity<R<Void>> handleActivityPrizeError(ActivityPrizeException exception) {
        var code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(R.error(code.code(), code.message()));
    }

    @ExceptionHandler(PrizeCatalogException.class)
    public ResponseEntity<R<Void>> handlePrizeError(PrizeCatalogException exception) {
        var code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(R.error(code.code(), code.message()));
    }

    @ExceptionHandler(RewardDefinitionException.class)
    public ResponseEntity<R<Void>> handleRewardDefinitionError(RewardDefinitionException exception) {
        var code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(R.error(code.code(), code.message()));
    }
}
