FROM mcr.microsoft.com/dotnet/sdk:8.0-alpine AS build
WORKDIR /src
COPY scenarios.csproj .
RUN dotnet restore
COPY *.cs .
RUN dotnet publish -c Release -o /out --no-restore
FROM mcr.microsoft.com/dotnet/aspnet:8.0-alpine
WORKDIR /app
COPY --from=build /out .
USER $APP_UID
EXPOSE 8080
CMD ["dotnet","scenarios.dll"]
