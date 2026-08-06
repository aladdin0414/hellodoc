# Build stage
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy source code
COPY . .

# Build frontend
RUN npm run build

# Runtime stage
FROM nginx:stable-alpine
WORKDIR /usr/share/nginx/html

# Copy build artifact
COPY --from=build /app/dist .

# Copy nginx config (will be mounted or replaced in compose)
# COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
