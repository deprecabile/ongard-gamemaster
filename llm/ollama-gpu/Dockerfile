FROM ollama/ollama:latest
RUN ollama serve & SERVER_PID=$! \
    && sleep 5 \
    && ollama pull VitoF/llama-3.1-8b-italian \
    && kill $SERVER_PID
