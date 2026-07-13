FROM ollama/ollama:latest
RUN ollama serve & SERVER_PID=$! \
    && sleep 5 \
    && ollama pull qwen3-embedding \
    && ollama pull qwen3:4b \
    && kill $SERVER_PID
