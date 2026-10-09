FROM ruby:3.4-alpine
RUN gem install webrick -v 1.9.1 --no-document
WORKDIR /app
COPY *.rb .
USER 65534:65534
EXPOSE 8080
CMD ["ruby","main.rb"]
