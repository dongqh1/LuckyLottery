package com.dongqh.lottery.exception;

import com.dongqh.lottery.exception.LotteryDomainException;
import com.dongqh.lottery.exception.activityprize.ActivityPrizeException;
import com.dongqh.lottery.exception.prizecatalog.PrizeCatalogException;
import com.dongqh.lottery.exception.prizecatalog.RewardDefinitionException;
import com.hmall.common.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dongqh.lottery")
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
