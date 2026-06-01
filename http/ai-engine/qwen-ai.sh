curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
-H "Authorization: Bearer sk-50239c760667494297501c8688ae8483" \
-H "Content-Type: application/json" \
-d '{
    "model": "qwen-math-turbo",
    "messages": [
        {
            "role": "user",
            "content": "你是谁？"
        }
    ]
}'