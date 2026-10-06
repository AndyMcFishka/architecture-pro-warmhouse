FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod *.go ./
RUN go test ./... && CGO_ENABLED=0 go build -o /temperature-api .

FROM scratch
COPY --from=builder /temperature-api /temperature-api
USER 65532:65532
EXPOSE 8081
ENTRYPOINT ["/temperature-api"]
