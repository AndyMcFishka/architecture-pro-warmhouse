FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py .
USER 65534:65534
EXPOSE 8081
CMD ["python", "-u", "main.py"]
