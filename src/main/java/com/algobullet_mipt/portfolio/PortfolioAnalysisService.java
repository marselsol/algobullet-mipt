package com.algobullet_mipt.portfolio;

import com.algobullet_mipt.domain.portfolio.port.AccountDataPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {

    private final AccountDataPort accountDataPort;

    public PortfolioAnalysis getAnalysis() {
        return accountDataPort.getPortfolioAnalysis();
    }

    public PortfolioAnalysis getStubAnalysis() {
        return getAnalysis();
    }
}
