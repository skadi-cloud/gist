FROM openjdk:22-jdk
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/install/server/ /app/
WORKDIR /app/bin
CMD ["./server"]