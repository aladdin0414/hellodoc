package com.nopkg.hellodoc.web.dto.ai;

public class AiCompletionResp {
    private String result;
    private String model;

    public AiCompletionResp() {}

    public AiCompletionResp(String result, String model) {
        this.result = result;
        this.model = model;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
