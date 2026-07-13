FROM ollama/ollama:latest

RUN echo 'FROM gemma3:12b' > /tmp/Modelfile && \
    echo 'PARAMETER num_ctx 8192' >> /tmp/Modelfile

RUN ollama serve & SERVER_PID=$! \
    && sleep 5 \
    && ollama pull gemma3:12b \
    && ollama create gemma3-8k -f /tmp/Modelfile \
    && rm /tmp/Modelfile \
    && kill $SERVER_PID